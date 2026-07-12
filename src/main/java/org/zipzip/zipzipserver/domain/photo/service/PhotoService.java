package org.zipzip.zipzipserver.domain.photo.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbumPhoto;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumPhotoRepository;
import org.zipzip.zipzipserver.domain.photo.code.PhotoErrorCode;
import org.zipzip.zipzipserver.domain.photo.code.PhotoSuccessCode;
import org.zipzip.zipzipserver.domain.photo.dto.request.PhotoIdsRequest;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoAttachResponse;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoBulkDeleteResponse;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoDetachResponse;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoListResponse;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoMetadataUpdateResponse;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.photo.entity.PhotoThumbnailStatus;
import org.zipzip.zipzipserver.domain.photo.repository.PhotoRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMembershipRepository;
import org.zipzip.zipzipserver.domain.storage.ObjectStorageService;
import org.zipzip.zipzipserver.domain.storage.PresignedDownload;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.global.cursor.OpaqueCursor;
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;

@Service
@RequiredArgsConstructor
public class PhotoService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_PHOTO_IDS_PER_REQUEST = 100;
    private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(10);
    private static final String BULK_DELETE_SCOPE_PREFIX = "PHOTO_BULK_DELETE:";
    private static final String ATTACH_SCOPE_PREFIX = "PHOTO_ATTACH:";
    private static final String DETACH_SCOPE_PREFIX = "PHOTO_DETACH:";
    private static final String HTTP_METHOD_POST = "POST";

    private final PhotoAccessGuard photoAccessGuard;
    private final PhotoRepository photoRepository;
    private final SharedAlbumPhotoRepository sharedAlbumPhotoRepository;
    private final SharedGroupMembershipRepository sharedGroupMembershipRepository;
    private final ObjectStorageService objectStorageService;
    private final IdempotencyService idempotencyService;
    private final Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public PhotoListResponse listPhotos(
            UUID sharedAlbumId, UUID appUserId, String cursor, Integer size) {
        SharedAlbum sharedAlbum =
                photoAccessGuard.requireActiveSharedAlbum(sharedAlbumId, appUserId);
        int pageSize = normalizeSize(size);

        Instant cursorDisplayAt = null;
        UUID cursorPhotoId = null;
        if (StringUtils.hasText(cursor)) {
            OpaqueCursor.Decoded decoded =
                    OpaqueCursor.decode(cursor, PhotoErrorCode.INVALID_CURSOR);
            cursorDisplayAt = decoded.timestamp();
            cursorPhotoId = decoded.id();
        }

        List<Photo> fetched =
                sharedAlbumPhotoRepository.findPageByActiveSharedAlbumId(
                        sharedAlbumId,
                        cursorDisplayAt,
                        cursorPhotoId,
                        PageRequest.of(0, pageSize + 1));

        boolean hasNext = fetched.size() > pageSize;
        List<Photo> pageItems = hasNext ? fetched.subList(0, pageSize) : fetched;

        List<PhotoListResponse.PhotoListItem> items =
                pageItems.stream().map(photo -> toListItem(photo, sharedAlbum, appUserId)).toList();

        String nextCursor = null;
        if (hasNext) {
            Photo last = pageItems.get(pageItems.size() - 1);
            nextCursor = OpaqueCursor.encode(displayAtOf(last), last.getId());
        }

        return new PhotoListResponse(items, nextCursor, hasNext);
    }

    @Transactional
    public PhotoMetadataUpdateResponse updateMetadata(
            UUID photoId, UUID appUserId, Map<String, Object> rawBody) {
        Photo photo = requireActivePhoto(photoId);
        photoAccessGuard.requireActivePhotoAccess(photo, appUserId);
        if (!photo.getUploadedByAppUser().getId().equals(appUserId)) {
            throw new BusinessException(PhotoErrorCode.NOT_PHOTO_UPLOADER);
        }

        if (rawBody.containsKey("takenAt")) {
            Object value = rawBody.get("takenAt");
            photo.updateTakenAt(value == null ? null : parseTakenAt(value.toString()));
        }
        applyLocationIfPresent(photo, rawBody);
        photoRepository.flush();

        return new PhotoMetadataUpdateResponse(
                photo.getId(),
                photo.getTakenAt(),
                displayAtOf(photo),
                photo.getLatitude(),
                photo.getLongitude(),
                photo.getLocationName(),
                photo.isInferred(),
                photo.getUpdatedAt());
    }

    @Transactional
    public PhotoBulkDeleteResponse bulkDelete(
            UUID sharedAlbumId,
            UUID appUserId,
            String idempotencyKeyHeader,
            PhotoIdsRequest request) {
        SharedAlbum sharedAlbum =
                photoAccessGuard.requireActiveSharedAlbum(sharedAlbumId, appUserId);
        UUID idempotencyKey = idempotencyService.parseIdempotencyKey(idempotencyKeyHeader);
        List<UUID> photoIds = validatePhotoIds(request.photoIds());

        String requestHash = idempotencyService.hashCanonicalRequest(request);
        String apiPath = "/api/v1/shared-albums/" + sharedAlbumId + "/photos/bulk-delete";
        IdempotencyService.IdempotencyStart<PhotoBulkDeleteResponse> idempotencyStart =
                idempotencyService.start(
                        BULK_DELETE_SCOPE_PREFIX + appUserId,
                        idempotencyKey,
                        HTTP_METHOD_POST,
                        apiPath,
                        requestHash,
                        PhotoBulkDeleteResponse.class);
        if (idempotencyStart.replayed()) {
            return idempotencyStart.replayResponse();
        }

        UUID sharedGroupId = sharedAlbum.getSharedGroup().getId();
        List<Photo> targets = new ArrayList<>();
        for (UUID photoId : photoIds) {
            if (!sharedAlbumPhotoRepository.existsBySharedAlbumIdAndPhotoId(
                    sharedAlbumId, photoId)) {
                throw new BusinessException(PhotoErrorCode.PHOTO_NOT_FOUND);
            }
            Photo photo = requireActivePhoto(photoId);
            requireDeletePermission(photo, appUserId, sharedGroupId);
            targets.add(photo);
        }

        Instant now = Instant.now(clock);
        targets.forEach(photo -> photo.delete(now));

        PhotoBulkDeleteResponse response = new PhotoBulkDeleteResponse(targets.size());
        idempotencyService.complete(
                idempotencyStart.record(), PhotoSuccessCode.PHOTOS_DELETED, response);
        return response;
    }

    @Transactional
    public PhotoAttachResponse attachPhotos(
            UUID sharedAlbumId,
            UUID appUserId,
            String idempotencyKeyHeader,
            PhotoIdsRequest request) {
        SharedAlbum sharedAlbum =
                photoAccessGuard.requireActiveSharedAlbum(sharedAlbumId, appUserId);
        UUID idempotencyKey = idempotencyService.parseIdempotencyKey(idempotencyKeyHeader);
        List<UUID> photoIds = validatePhotoIds(request.photoIds());

        String requestHash = idempotencyService.hashCanonicalRequest(request);
        String apiPath = "/api/v1/shared-albums/" + sharedAlbumId + "/photos/attach";
        IdempotencyService.IdempotencyStart<PhotoAttachResponse> idempotencyStart =
                idempotencyService.start(
                        ATTACH_SCOPE_PREFIX + appUserId,
                        idempotencyKey,
                        HTTP_METHOD_POST,
                        apiPath,
                        requestHash,
                        PhotoAttachResponse.class);
        if (idempotencyStart.replayed()) {
            return idempotencyStart.replayResponse();
        }

        UUID targetGroupId = sharedAlbum.getSharedGroup().getId();
        int attachedCount = 0;
        int alreadyAttachedCount = 0;
        for (UUID photoId : photoIds) {
            Photo photo = requireActivePhoto(photoId);
            UUID photoGroupId = photoAccessGuard.resolveActiveSharedGroupId(photo);
            if (!photoGroupId.equals(targetGroupId)) {
                throw new BusinessException(PhotoErrorCode.PHOTO_NOT_IN_SAME_SHARED_GROUP);
            }
            if (sharedAlbumPhotoRepository.existsBySharedAlbumIdAndPhotoId(
                    sharedAlbumId, photoId)) {
                alreadyAttachedCount++;
            } else {
                sharedAlbumPhotoRepository.save(SharedAlbumPhoto.create(sharedAlbum, photo));
                attachedCount++;
            }
        }

        PhotoAttachResponse response = new PhotoAttachResponse(attachedCount, alreadyAttachedCount);
        idempotencyService.complete(
                idempotencyStart.record(), PhotoSuccessCode.PHOTOS_ATTACHED, response);
        return response;
    }

    @Transactional
    public PhotoDetachResponse detachPhotos(
            UUID sharedAlbumId,
            UUID appUserId,
            String idempotencyKeyHeader,
            PhotoIdsRequest request) {
        photoAccessGuard.requireActiveSharedAlbum(sharedAlbumId, appUserId);
        UUID idempotencyKey = idempotencyService.parseIdempotencyKey(idempotencyKeyHeader);
        List<UUID> photoIds = validatePhotoIds(request.photoIds());

        String requestHash = idempotencyService.hashCanonicalRequest(request);
        String apiPath = "/api/v1/shared-albums/" + sharedAlbumId + "/photos/detach";
        IdempotencyService.IdempotencyStart<PhotoDetachResponse> idempotencyStart =
                idempotencyService.start(
                        DETACH_SCOPE_PREFIX + appUserId,
                        idempotencyKey,
                        HTTP_METHOD_POST,
                        apiPath,
                        requestHash,
                        PhotoDetachResponse.class);
        if (idempotencyStart.replayed()) {
            return idempotencyStart.replayResponse();
        }

        Instant now = Instant.now(clock);
        int detachedCount = 0;
        int deletedPhotoCount = 0;
        for (UUID photoId : photoIds) {
            Optional<SharedAlbumPhoto> mapping =
                    sharedAlbumPhotoRepository.findBySharedAlbumIdAndPhotoId(
                            sharedAlbumId, photoId);
            if (mapping.isEmpty()) {
                continue;
            }
            sharedAlbumPhotoRepository.delete(mapping.get());
            sharedAlbumPhotoRepository.flush();
            detachedCount++;

            if (sharedAlbumPhotoRepository.countByPhotoId(photoId) == 0) {
                Photo photo = photoRepository.findById(photoId).orElse(null);
                if (photo != null && photo.getDeletedAt() == null) {
                    photo.delete(now);
                    deletedPhotoCount++;
                }
            }
        }

        PhotoDetachResponse response = new PhotoDetachResponse(detachedCount, deletedPhotoCount);
        idempotencyService.complete(
                idempotencyStart.record(), PhotoSuccessCode.PHOTOS_DETACHED, response);
        return response;
    }

    private void applyLocationIfPresent(Photo photo, Map<String, Object> rawBody) {
        boolean hasLatitude = rawBody.containsKey("latitude");
        boolean hasLongitude = rawBody.containsKey("longitude");
        boolean hasLocationName = rawBody.containsKey("locationName");
        if (!hasLatitude && !hasLongitude && !hasLocationName) {
            return;
        }
        if (!(hasLatitude && hasLongitude && hasLocationName)) {
            throw new BusinessException(PhotoErrorCode.INVALID_PHOTO_LOCATION);
        }

        Object latitude = rawBody.get("latitude");
        Object longitude = rawBody.get("longitude");
        Object locationName = rawBody.get("locationName");
        if (latitude == null && longitude == null && locationName == null) {
            photo.applyLocation(null, null, null, false);
            return;
        }
        if (latitude == null || longitude == null || locationName == null) {
            throw new BusinessException(PhotoErrorCode.INVALID_PHOTO_LOCATION);
        }

        boolean isInferred = Boolean.TRUE.equals(rawBody.get("isInferred"));
        photo.applyLocation(
                toDouble(latitude), toDouble(longitude), locationName.toString(), isInferred);
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException exception) {
            throw new BusinessException(PhotoErrorCode.INVALID_PHOTO_LOCATION);
        }
    }

    private void requireDeletePermission(Photo photo, UUID appUserId, UUID sharedGroupId) {
        AppUser uploader = photo.getUploadedByAppUser();
        if (uploader.getId().equals(appUserId)) {
            return;
        }
        if (uploader.isDeleted() && isHost(sharedGroupId, appUserId)) {
            return;
        }
        throw new BusinessException(PhotoErrorCode.NOT_PHOTO_UPLOADER);
    }

    private boolean isHost(UUID sharedGroupId, UUID appUserId) {
        return sharedGroupMembershipRepository
                .findActiveBySharedGroupIdAndAppUserId(sharedGroupId, appUserId)
                .map(membership -> membership.getRole() == SharedGroupRole.HOST)
                .orElse(false);
    }

    private Photo requireActivePhoto(UUID photoId) {
        return photoRepository
                .findById(photoId)
                .filter(photo -> photo.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(PhotoErrorCode.PHOTO_NOT_FOUND));
    }

    private List<UUID> validatePhotoIds(List<UUID> photoIds) {
        if (photoIds == null
                || photoIds.isEmpty()
                || photoIds.size() > MAX_PHOTO_IDS_PER_REQUEST
                || new HashSet<>(photoIds).size() != photoIds.size()) {
            throw new BusinessException(PhotoErrorCode.INVALID_PHOTO_IDS);
        }
        return photoIds;
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(MAX_PAGE_SIZE, size));
    }

    private Instant displayAtOf(Photo photo) {
        return photo.getTakenAt() != null ? photo.getTakenAt() : photo.getCreatedAt();
    }

    private Instant parseTakenAt(String takenAt) {
        try {
            return Instant.parse(takenAt);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(PhotoErrorCode.INVALID_TAKEN_AT);
        }
    }

    private PhotoListResponse.PhotoListItem toListItem(
            Photo photo, SharedAlbum sharedAlbum, UUID appUserId) {
        String thumbnailUrl = null;
        Instant thumbnailUrlExpiresAt = null;
        if (photo.getThumbnailStatus() == PhotoThumbnailStatus.READY
                && photo.getThumbnailObjectKey() != null) {
            PresignedDownload thumbnail =
                    objectStorageService.issueDownloadUrl(
                            photo.getThumbnailObjectKey(), DOWNLOAD_URL_TTL);
            thumbnailUrl = thumbnail.url();
            thumbnailUrlExpiresAt = thumbnail.expiresAt();
        }

        PresignedDownload original =
                objectStorageService.issueDownloadUrl(
                        photo.getOriginalObjectKey(), DOWNLOAD_URL_TTL);
        AppUser uploader = photo.getUploadedByAppUser();

        return new PhotoListResponse.PhotoListItem(
                photo.getId(),
                sharedAlbum.getSharedGroup().getId(),
                sharedAlbum.getId(),
                original.url(),
                original.expiresAt(),
                thumbnailUrl,
                thumbnailUrlExpiresAt,
                photo.getThumbnailStatus().name(),
                photo.getDeviceModel(),
                photo.getTakenAt(),
                displayAtOf(photo),
                photo.getLatitude(),
                photo.getLongitude(),
                photo.getLocationName(),
                photo.isInferred(),
                photo.getWidth(),
                photo.getHeight(),
                new PhotoListResponse.Uploader(uploader.getId(), uploader.getDisplayName()),
                uploader.getId().equals(appUserId),
                0L,
                0L,
                false,
                photo.getCreatedAt());
    }
}
