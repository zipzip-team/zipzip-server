package org.zipzip.zipzipserver.domain.photo.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbumPhoto;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumPhotoRepository;
import org.zipzip.zipzipserver.domain.photo.code.PhotoErrorCode;
import org.zipzip.zipzipserver.domain.photo.code.PhotoSuccessCode;
import org.zipzip.zipzipserver.domain.photo.dto.request.PhotoUploadCompleteRequest;
import org.zipzip.zipzipserver.domain.photo.dto.request.PhotoUploadUrlRequest;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoUploadCompleteResponse;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoUploadItemResponse;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoUploadUrlResponse;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.photo.entity.PhotoUploadReservation;
import org.zipzip.zipzipserver.domain.photo.repository.PhotoRepository;
import org.zipzip.zipzipserver.domain.photo.repository.PhotoUploadReservationRepository;
import org.zipzip.zipzipserver.domain.storage.ObjectKeyGenerator;
import org.zipzip.zipzipserver.domain.storage.ObjectStorageService;
import org.zipzip.zipzipserver.domain.storage.PresignedDownload;
import org.zipzip.zipzipserver.domain.storage.PresignedUpload;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.domain.user.repository.AppUserRepository;
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;

@Service
@RequiredArgsConstructor
public class PhotoUploadService {

    private static final int MAX_FILES_PER_REQUEST = 20;
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;
    private static final int MAX_DEVICE_MODEL_LENGTH = 100;
    private static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(15);
    private static final Duration RESERVATION_TTL = Duration.ofMinutes(15);
    private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(10);
    private static final String UPLOAD_COMPLETE_SCOPE_PREFIX = "PHOTO_UPLOAD_COMPLETE:";
    private static final String UPLOAD_COMPLETE_HTTP_METHOD = "POST";

    private final PhotoAccessGuard photoAccessGuard;
    private final PhotoRepository photoRepository;
    private final PhotoUploadReservationRepository photoUploadReservationRepository;
    private final SharedAlbumPhotoRepository sharedAlbumPhotoRepository;
    private final AppUserRepository appUserRepository;
    private final ObjectStorageService objectStorageService;
    private final ObjectKeyGenerator objectKeyGenerator;
    private final IdempotencyService idempotencyService;
    private final ThumbnailProcessingService thumbnailProcessingService;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public PhotoUploadUrlResponse issueUploadUrls(
            UUID sharedAlbumId, UUID appUserId, PhotoUploadUrlRequest request) {
        SharedAlbum sharedAlbum =
                photoAccessGuard.requireActiveSharedAlbum(sharedAlbumId, appUserId);
        List<PhotoUploadUrlRequest.UploadUrlFileSpec> files = validateFileSpecs(request.files());

        AppUser requester = appUserRepository.getReferenceById(appUserId);
        Instant expiresAt = Instant.now(clock).plus(RESERVATION_TTL);

        List<PhotoUploadUrlResponse.UploadUrlItem> uploads = new ArrayList<>();
        for (PhotoUploadUrlRequest.UploadUrlFileSpec file : files) {
            String objectKey = objectKeyGenerator.generateOriginalKey(file.contentType());
            PresignedUpload presigned =
                    objectStorageService.issueUploadUrl(
                            objectKey, file.contentType(), file.sizeBytes(), UPLOAD_URL_TTL);
            photoUploadReservationRepository.save(
                    PhotoUploadReservation.create(objectKey, sharedAlbum, requester, expiresAt));
            uploads.add(
                    new PhotoUploadUrlResponse.UploadUrlItem(
                            objectKey, presigned.uploadUrl(), presigned.expiresAt()));
        }
        return new PhotoUploadUrlResponse(uploads);
    }

    @Transactional
    public PhotoUploadCompleteResult completeUpload(
            UUID sharedAlbumId,
            UUID appUserId,
            String idempotencyKeyHeader,
            PhotoUploadCompleteRequest request) {
        SharedAlbum sharedAlbum =
                photoAccessGuard.requireActiveSharedAlbum(sharedAlbumId, appUserId);
        UUID idempotencyKey = idempotencyService.parseIdempotencyKey(idempotencyKeyHeader);
        List<PhotoUploadCompleteRequest.CompleteFileSpec> files =
                validateCompleteFileSpecs(request.files());

        String requestHash = idempotencyService.hashCanonicalRequest(request);
        String scope = UPLOAD_COMPLETE_SCOPE_PREFIX + appUserId;
        String apiPath = "/api/v1/shared-albums/" + sharedAlbumId + "/photos/complete";
        IdempotencyService.IdempotencyStart<PhotoUploadCompleteResponse> idempotencyStart =
                idempotencyService.start(
                        scope,
                        idempotencyKey,
                        UPLOAD_COMPLETE_HTTP_METHOD,
                        apiPath,
                        requestHash,
                        PhotoUploadCompleteResponse.class);

        if (idempotencyStart.replayed()) {
            return new PhotoUploadCompleteResult(idempotencyStart.replayResponse(), true);
        }

        AppUser requester = appUserRepository.getReferenceById(appUserId);
        Instant now = Instant.now(clock);
        List<Photo> createdPhotos = new ArrayList<>();

        for (PhotoUploadCompleteRequest.CompleteFileSpec file : files) {
            PhotoUploadReservation reservation =
                    photoUploadReservationRepository
                            .findById(file.objectKey())
                            .orElseThrow(
                                    () ->
                                            new BusinessException(
                                                    PhotoErrorCode.UPLOAD_OBJECT_NOT_FOUND));
            if (!reservation.isUsableBy(sharedAlbum, requester, now)) {
                throw new BusinessException(PhotoErrorCode.UPLOAD_OBJECT_NOT_FOUND);
            }
            if (!objectStorageService.exists(file.objectKey())) {
                throw new BusinessException(PhotoErrorCode.UPLOAD_NOT_COMPLETED);
            }

            Photo photo =
                    Photo.create(
                            requester,
                            normalizeDeviceModel(file.deviceModel()),
                            file.objectKey(),
                            parseTakenAt(file.takenAt()),
                            file.width(),
                            file.height());
            if (file.latitude() != null) {
                photo.applyLocation(
                        file.latitude(),
                        file.longitude(),
                        file.locationName(),
                        Boolean.TRUE.equals(file.isInferred()));
            }
            Photo savedPhoto = photoRepository.save(photo);
            sharedAlbumPhotoRepository.save(SharedAlbumPhoto.create(sharedAlbum, savedPhoto));
            photoUploadReservationRepository.delete(reservation);
            createdPhotos.add(savedPhoto);
        }
        photoRepository.flush();

        PhotoUploadCompleteResponse response =
                new PhotoUploadCompleteResponse(
                        createdPhotos.stream()
                                .map(photo -> toItemResponse(photo, sharedAlbum))
                                .toList());
        idempotencyService.complete(
                idempotencyStart.record(), PhotoSuccessCode.PHOTOS_CREATED, response);

        submitThumbnailJobsAfterCommit(createdPhotos.stream().map(Photo::getId).toList());

        return new PhotoUploadCompleteResult(response, false);
    }

    private void submitThumbnailJobsAfterCommit(List<UUID> photoIds) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        photoIds.forEach(thumbnailProcessingService::process);
                    }
                });
    }

    private List<PhotoUploadUrlRequest.UploadUrlFileSpec> validateFileSpecs(
            List<PhotoUploadUrlRequest.UploadUrlFileSpec> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(PhotoErrorCode.INVALID_UPLOAD_METADATA);
        }
        if (files.size() > MAX_FILES_PER_REQUEST) {
            throw new BusinessException(PhotoErrorCode.TOO_MANY_FILES);
        }
        for (PhotoUploadUrlRequest.UploadUrlFileSpec file : files) {
            if (!StringUtils.hasText(file.contentType())
                    || file.sizeBytes() == null
                    || file.sizeBytes() <= 0) {
                throw new BusinessException(PhotoErrorCode.INVALID_UPLOAD_METADATA);
            }
            if (file.sizeBytes() > MAX_FILE_SIZE_BYTES) {
                throw new BusinessException(PhotoErrorCode.FILE_TOO_LARGE);
            }
            if (!file.contentType().startsWith("image/")) {
                throw new BusinessException(PhotoErrorCode.UNSUPPORTED_IMAGE_TYPE);
            }
        }
        return files;
    }

    private List<PhotoUploadCompleteRequest.CompleteFileSpec> validateCompleteFileSpecs(
            List<PhotoUploadCompleteRequest.CompleteFileSpec> files) {
        if (files == null || files.isEmpty() || files.size() > MAX_FILES_PER_REQUEST) {
            throw new BusinessException(PhotoErrorCode.INVALID_UPLOAD_METADATA);
        }
        Set<String> seenObjectKeys = new HashSet<>();
        for (PhotoUploadCompleteRequest.CompleteFileSpec file : files) {
            if (!StringUtils.hasText(file.objectKey()) || !seenObjectKeys.add(file.objectKey())) {
                throw new BusinessException(PhotoErrorCode.INVALID_UPLOAD_METADATA);
            }
        }
        return files;
    }

    private String normalizeDeviceModel(String deviceModel) {
        if (deviceModel == null) {
            return null;
        }
        String trimmed = deviceModel.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_DEVICE_MODEL_LENGTH) {
            throw new BusinessException(PhotoErrorCode.INVALID_UPLOAD_METADATA);
        }
        return trimmed;
    }

    private Instant parseTakenAt(String takenAt) {
        if (takenAt == null) {
            return null;
        }
        try {
            return Instant.parse(takenAt);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(PhotoErrorCode.INVALID_UPLOAD_METADATA);
        }
    }

    private PhotoUploadItemResponse toItemResponse(Photo photo, SharedAlbum sharedAlbum) {
        PresignedDownload originalUrl =
                objectStorageService.issueDownloadUrl(
                        photo.getOriginalObjectKey(), DOWNLOAD_URL_TTL);
        return new PhotoUploadItemResponse(
                photo.getId(),
                sharedAlbum.getSharedGroup().getId(),
                sharedAlbum.getId(),
                originalUrl.url(),
                originalUrl.expiresAt(),
                null,
                null,
                photo.getThumbnailStatus().name(),
                photo.getDeviceModel(),
                photo.getTakenAt(),
                photo.getLatitude(),
                photo.getLongitude(),
                photo.getLocationName(),
                photo.isInferred(),
                photo.getWidth(),
                photo.getHeight(),
                photo.getCreatedAt());
    }

    public record PhotoUploadCompleteResult(
            PhotoUploadCompleteResponse response, boolean replayed) {}
}
