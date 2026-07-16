package org.zipzip.zipzipserver.domain.album.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.zipzip.zipzipserver.domain.album.code.SharedAlbumErrorCode;
import org.zipzip.zipzipserver.domain.album.code.SharedAlbumSuccessCode;
import org.zipzip.zipzipserver.domain.album.dto.request.SharedAlbumIdsRequest;
import org.zipzip.zipzipserver.domain.album.dto.request.SharedAlbumNameRequest;
import org.zipzip.zipzipserver.domain.album.dto.response.SharedAlbumBulkDeleteResponse;
import org.zipzip.zipzipserver.domain.album.dto.response.SharedAlbumListResponse;
import org.zipzip.zipzipserver.domain.album.dto.response.SharedAlbumRenameResponse;
import org.zipzip.zipzipserver.domain.album.dto.response.SharedAlbumResponse;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumPhotoRepository;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumRepository;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.photo.entity.PhotoThumbnailStatus;
import org.zipzip.zipzipserver.domain.photo.repository.PhotoRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.storage.ObjectStorageService;
import org.zipzip.zipzipserver.domain.storage.PresignedDownload;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.domain.user.repository.AppUserRepository;
import org.zipzip.zipzipserver.global.cursor.OpaqueCursor;
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;

@Service
@RequiredArgsConstructor
public class SharedAlbumService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_SHARED_ALBUM_IDS_PER_REQUEST = 100;
    private static final int THUMBNAIL_COUNT = 3;
    private static final Duration THUMBNAIL_URL_TTL = Duration.ofMinutes(10);
    private static final String CREATE_SCOPE_PREFIX = "SHARED_ALBUM_CREATE:";
    private static final String BULK_DELETE_SCOPE_PREFIX = "SHARED_ALBUM_BULK_DELETE:";
    private static final String HTTP_METHOD_POST = "POST";
    private static final String BULK_DELETE_API_PATH = "/api/v1/shared-albums/bulk-delete";

    private final SharedAlbumAccessGuard sharedAlbumAccessGuard;
    private final SharedAlbumRepository sharedAlbumRepository;
    private final SharedAlbumPhotoRepository sharedAlbumPhotoRepository;
    private final PhotoRepository photoRepository;
    private final AppUserRepository appUserRepository;
    private final ObjectStorageService objectStorageService;
    private final IdempotencyService idempotencyService;
    private final Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public SharedAlbumListResponse listAlbums(
            UUID sharedGroupId, UUID appUserId, String cursor, Integer size) {
        sharedAlbumAccessGuard.requireActiveSharedGroup(sharedGroupId, appUserId);
        int pageSize = normalizeSize(size);

        Instant cursorCreatedAt = null;
        UUID cursorId = null;
        if (StringUtils.hasText(cursor)) {
            OpaqueCursor.Decoded decoded =
                    OpaqueCursor.decode(cursor, SharedAlbumErrorCode.INVALID_CURSOR);
            cursorCreatedAt = decoded.timestamp();
            cursorId = decoded.id();
        }

        List<SharedAlbum> fetched =
                sharedAlbumRepository.findPageByActiveSharedGroupId(
                        sharedGroupId, cursorCreatedAt, cursorId, PageRequest.of(0, pageSize + 1));

        boolean hasNext = fetched.size() > pageSize;
        List<SharedAlbum> pageItems = hasNext ? fetched.subList(0, pageSize) : fetched;

        List<SharedAlbumListResponse.Item> items =
                pageItems.stream().map(album -> toListItem(album, appUserId)).toList();

        String nextCursor = null;
        if (hasNext) {
            SharedAlbum last = pageItems.get(pageItems.size() - 1);
            nextCursor = OpaqueCursor.encode(last.getCreatedAt(), last.getId());
        }

        return new SharedAlbumListResponse(items, nextCursor, hasNext);
    }

    @Transactional
    public SharedAlbumResponse createAlbum(
            UUID sharedGroupId,
            UUID appUserId,
            String idempotencyKeyHeader,
            SharedAlbumNameRequest request) {
        SharedGroup sharedGroup =
                sharedAlbumAccessGuard.requireActiveSharedGroup(sharedGroupId, appUserId);
        String name = validateName(request.name());
        UUID idempotencyKey = idempotencyService.parseIdempotencyKey(idempotencyKeyHeader);

        String requestHash = idempotencyService.hashCanonicalRequest(request);
        String apiPath = "/api/v1/shared-groups/" + sharedGroupId + "/shared-albums";
        IdempotencyService.IdempotencyStart<SharedAlbumResponse> idempotencyStart =
                idempotencyService.start(
                        CREATE_SCOPE_PREFIX + appUserId,
                        idempotencyKey,
                        HTTP_METHOD_POST,
                        apiPath,
                        requestHash,
                        SharedAlbumResponse.class);
        if (idempotencyStart.replayed()) {
            return idempotencyStart.replayResponse();
        }

        AppUser creator = appUserRepository.getReferenceById(appUserId);
        SharedAlbum album =
                sharedAlbumRepository.saveAndFlush(SharedAlbum.create(sharedGroup, creator, name));

        SharedAlbumResponse response =
                new SharedAlbumResponse(
                        album.getId(),
                        sharedGroup.getId(),
                        album.getName(),
                        0L,
                        new SharedAlbumResponse.Creator(creator.getId(), creator.getDisplayName()),
                        true,
                        album.getCreatedAt(),
                        album.getUpdatedAt());
        idempotencyService.complete(
                idempotencyStart.record(), SharedAlbumSuccessCode.SHARED_ALBUM_CREATED, response);
        return response;
    }

    @Transactional(readOnly = true)
    public SharedAlbumResponse getAlbum(UUID sharedAlbumId, UUID appUserId) {
        SharedAlbum album =
                sharedAlbumAccessGuard.requireActiveSharedAlbum(sharedAlbumId, appUserId);
        return toResponse(album, appUserId);
    }

    @Transactional
    public SharedAlbumRenameResponse renameAlbum(
            UUID sharedAlbumId, UUID appUserId, SharedAlbumNameRequest request) {
        SharedAlbum album =
                sharedAlbumAccessGuard.requireActiveSharedAlbum(sharedAlbumId, appUserId);
        String name = validateName(request.name());
        album.updateName(name);
        sharedAlbumRepository.flush();
        return new SharedAlbumRenameResponse(album.getId(), album.getName(), album.getUpdatedAt());
    }

    @Transactional
    public void deleteAlbum(UUID sharedAlbumId, UUID appUserId) {
        SharedAlbum album =
                sharedAlbumAccessGuard.requireActiveSharedAlbum(sharedAlbumId, appUserId);

        List<UUID> photoIds =
                sharedAlbumPhotoRepository.findBySharedAlbumId(sharedAlbumId).stream()
                        .map(mapping -> mapping.getPhoto().getId())
                        .distinct()
                        .toList();

        sharedAlbumPhotoRepository.deleteBySharedAlbumId(sharedAlbumId);
        sharedAlbumPhotoRepository.flush();

        Instant now = Instant.now(clock);
        for (UUID photoId : photoIds) {
            if (sharedAlbumPhotoRepository.countByPhotoId(photoId) == 0) {
                Photo photo = photoRepository.findById(photoId).orElse(null);
                if (photo != null && photo.getDeletedAt() == null) {
                    photo.delete(now);
                }
            }
        }

        album.delete(now);
    }

    @Transactional
    public SharedAlbumBulkDeleteResponse bulkDeleteAlbums(
            UUID appUserId, String idempotencyKeyHeader, SharedAlbumIdsRequest request) {
        UUID idempotencyKey = idempotencyService.parseIdempotencyKey(idempotencyKeyHeader);
        List<UUID> sharedAlbumIds =
                validateSharedAlbumIds(request == null ? null : request.sharedAlbumIds());

        String requestHash = idempotencyService.hashCanonicalRequest(request);
        IdempotencyService.IdempotencyStart<SharedAlbumBulkDeleteResponse> idempotencyStart =
                idempotencyService.start(
                        BULK_DELETE_SCOPE_PREFIX + appUserId,
                        idempotencyKey,
                        HTTP_METHOD_POST,
                        BULK_DELETE_API_PATH,
                        requestHash,
                        SharedAlbumBulkDeleteResponse.class);
        if (idempotencyStart.replayed()) {
            return idempotencyStart.replayResponse();
        }

        List<SharedAlbum> targets = new ArrayList<>();
        for (UUID sharedAlbumId : sharedAlbumIds) {
            targets.add(sharedAlbumAccessGuard.requireActiveSharedAlbum(sharedAlbumId, appUserId));
        }

        Set<UUID> photoIds = new HashSet<>();
        for (SharedAlbum album : targets) {
            sharedAlbumPhotoRepository.findBySharedAlbumId(album.getId()).stream()
                    .map(mapping -> mapping.getPhoto().getId())
                    .forEach(photoIds::add);
            sharedAlbumPhotoRepository.deleteBySharedAlbumId(album.getId());
        }
        sharedAlbumPhotoRepository.flush();

        Instant now = Instant.now(clock);
        int deletedPhotoCount = 0;
        for (UUID photoId : photoIds) {
            if (sharedAlbumPhotoRepository.countByPhotoId(photoId) == 0) {
                Photo photo = photoRepository.findById(photoId).orElse(null);
                if (photo != null && photo.getDeletedAt() == null) {
                    photo.delete(now);
                    deletedPhotoCount++;
                }
            }
        }

        targets.forEach(album -> album.delete(now));

        SharedAlbumBulkDeleteResponse response =
                new SharedAlbumBulkDeleteResponse(targets.size(), deletedPhotoCount);
        idempotencyService.complete(
                idempotencyStart.record(), SharedAlbumSuccessCode.SHARED_ALBUMS_DELETED, response);
        return response;
    }

    private String validateName(String rawName) {
        if (rawName == null) {
            throw new BusinessException(SharedAlbumErrorCode.INVALID_SHARED_ALBUM_NAME);
        }
        String trimmed = rawName.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(SharedAlbumErrorCode.INVALID_SHARED_ALBUM_NAME);
        }
        return trimmed;
    }

    private List<UUID> validateSharedAlbumIds(List<UUID> sharedAlbumIds) {
        if (sharedAlbumIds == null
                || sharedAlbumIds.isEmpty()
                || sharedAlbumIds.size() > MAX_SHARED_ALBUM_IDS_PER_REQUEST
                || sharedAlbumIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(sharedAlbumIds).size() != sharedAlbumIds.size()) {
            throw new BusinessException(SharedAlbumErrorCode.INVALID_SHARED_ALBUM_IDS);
        }
        return sharedAlbumIds;
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(MAX_PAGE_SIZE, size));
    }

    private SharedAlbumListResponse.Item toListItem(SharedAlbum album, UUID appUserId) {
        long photoCount =
                sharedAlbumPhotoRepository.countBySharedAlbumIdAndPhotoDeletedAtIsNull(
                        album.getId());
        List<SharedAlbumListResponse.Thumbnail> thumbnails = toThumbnails(album.getId());
        AppUser creator = album.getCreatedByAppUser();
        return new SharedAlbumListResponse.Item(
                album.getId(),
                album.getName(),
                photoCount,
                thumbnails,
                new SharedAlbumResponse.Creator(creator.getId(), creator.getDisplayName()),
                creator.getId().equals(appUserId),
                album.getCreatedAt(),
                album.getUpdatedAt());
    }

    private List<SharedAlbumListResponse.Thumbnail> toThumbnails(UUID albumId) {
        List<Photo> oldestPhotos =
                sharedAlbumPhotoRepository.findOldestPhotosBySharedAlbumIdAndThumbnailStatus(
                        albumId, PhotoThumbnailStatus.READY, PageRequest.of(0, THUMBNAIL_COUNT));
        return oldestPhotos.stream()
                .map(
                        photo -> {
                            PresignedDownload thumbnail =
                                    objectStorageService.issueDownloadUrl(
                                            photo.getThumbnailObjectKey(), THUMBNAIL_URL_TTL);
                            return new SharedAlbumListResponse.Thumbnail(
                                    thumbnail.url(), thumbnail.expiresAt());
                        })
                .toList();
    }

    private SharedAlbumResponse toResponse(SharedAlbum album, UUID appUserId) {
        long photoCount =
                sharedAlbumPhotoRepository.countBySharedAlbumIdAndPhotoDeletedAtIsNull(
                        album.getId());
        AppUser creator = album.getCreatedByAppUser();
        return new SharedAlbumResponse(
                album.getId(),
                album.getSharedGroup().getId(),
                album.getName(),
                photoCount,
                new SharedAlbumResponse.Creator(creator.getId(), creator.getDisplayName()),
                creator.getId().equals(appUserId),
                album.getCreatedAt(),
                album.getUpdatedAt());
    }
}
