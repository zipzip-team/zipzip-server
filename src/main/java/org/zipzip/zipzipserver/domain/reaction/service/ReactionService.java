package org.zipzip.zipzipserver.domain.reaction.service;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbumPhoto;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumPhotoRepository;
import org.zipzip.zipzipserver.domain.photo.code.PhotoErrorCode;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.photo.entity.PhotoThumbnailStatus;
import org.zipzip.zipzipserver.domain.photo.repository.PhotoRepository;
import org.zipzip.zipzipserver.domain.photo.service.PhotoAccessGuard;
import org.zipzip.zipzipserver.domain.reaction.code.ReactionErrorCode;
import org.zipzip.zipzipserver.domain.reaction.code.ReactionSuccessCode;
import org.zipzip.zipzipserver.domain.reaction.dto.request.PhotoCommentCreateRequest;
import org.zipzip.zipzipserver.domain.reaction.dto.response.PhotoCommentListResponse;
import org.zipzip.zipzipserver.domain.reaction.dto.response.PhotoCommentResponse;
import org.zipzip.zipzipserver.domain.reaction.dto.response.PhotoDetailResponse;
import org.zipzip.zipzipserver.domain.reaction.dto.response.PhotoLikeResponse;
import org.zipzip.zipzipserver.domain.reaction.entity.PhotoComment;
import org.zipzip.zipzipserver.domain.reaction.repository.PhotoCommentRepository;
import org.zipzip.zipzipserver.domain.reaction.repository.PhotoLikeRepository;
import org.zipzip.zipzipserver.domain.storage.ObjectStorageService;
import org.zipzip.zipzipserver.domain.storage.PresignedDownload;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.global.cursor.OpaqueCursor;
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;

@Service
@RequiredArgsConstructor
public class ReactionService {

    private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(10);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_COMMENT_CONTENT_LENGTH = 1000;
    private static final String COMMENT_CREATE_SCOPE_PREFIX = "PHOTO_COMMENT_CREATE:";
    private static final String HTTP_METHOD_POST = "POST";

    private final PhotoRepository photoRepository;
    private final PhotoAccessGuard photoAccessGuard;
    private final SharedAlbumPhotoRepository sharedAlbumPhotoRepository;
    private final PhotoLikeRepository photoLikeRepository;
    private final PhotoCommentRepository photoCommentRepository;
    private final ObjectStorageService objectStorageService;
    private final IdempotencyService idempotencyService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public PhotoDetailResponse getPhotoDetail(UUID photoId, UUID appUserId) {
        Photo photo = requireActivePhoto(photoId);
        photoAccessGuard.requireActivePhotoAccess(photo, appUserId);

        List<SharedAlbumPhoto> activeAlbumPhotos =
                sharedAlbumPhotoRepository.findByPhotoId(photoId).stream()
                        .filter(mapping -> mapping.getSharedAlbum().getDeletedAt() == null)
                        .toList();
        UUID sharedGroupId =
                activeAlbumPhotos.stream()
                        .findFirst()
                        .map(mapping -> mapping.getSharedAlbum().getSharedGroup().getId())
                        .orElseThrow(() -> new BusinessException(PhotoErrorCode.PHOTO_NOT_FOUND));

        PresignedDownload original =
                objectStorageService.issueDownloadUrl(
                        photo.getOriginalObjectKey(), DOWNLOAD_URL_TTL);
        PresignedDownload thumbnail = issueThumbnailDownload(photo);
        AppUser uploader = photo.getUploadedByAppUser();

        return new PhotoDetailResponse(
                photo.getId(),
                sharedGroupId,
                activeAlbumPhotos.stream()
                        .map(mapping -> mapping.getSharedAlbum().getId())
                        .toList(),
                original.url(),
                original.expiresAt(),
                thumbnail == null ? null : thumbnail.url(),
                thumbnail == null ? null : thumbnail.expiresAt(),
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
                new PhotoDetailResponse.Uploader(uploader.getId(), uploader.getDisplayName()),
                uploader.getId().equals(appUserId),
                photoLikeRepository.countByPhotoId(photoId),
                photoCommentRepository.countByPhotoId(photoId),
                photoLikeRepository.existsByPhotoIdAndAppUserId(photoId, appUserId),
                photo.getCreatedAt(),
                photo.getUpdatedAt());
    }

    @Transactional
    public PhotoLikeResponse likePhoto(UUID photoId, UUID appUserId) {
        Photo photo = requireActivePhoto(photoId);
        photoAccessGuard.requireActivePhotoAccess(photo, appUserId);

        photoLikeRepository.insertIfAbsent(UUID.randomUUID(), photoId, appUserId);

        return new PhotoLikeResponse(photoId, true, photoLikeRepository.countByPhotoId(photoId));
    }

    @Transactional
    public PhotoLikeResponse unlikePhoto(UUID photoId, UUID appUserId) {
        Photo photo = requireActivePhoto(photoId);
        photoAccessGuard.requireActivePhotoAccess(photo, appUserId);
        photoLikeRepository.deleteByPhotoIdAndAppUserId(photoId, appUserId);

        return new PhotoLikeResponse(photoId, false, photoLikeRepository.countByPhotoId(photoId));
    }

    @Transactional(readOnly = true)
    public PhotoCommentListResponse listComments(
            UUID photoId, UUID appUserId, String cursor, Integer size) {
        Photo photo = requireActivePhoto(photoId);
        photoAccessGuard.requireActivePhotoAccess(photo, appUserId);
        int pageSize = normalizeSize(size);

        Instant cursorCreatedAt = null;
        UUID cursorCommentId = null;
        if (StringUtils.hasText(cursor)) {
            OpaqueCursor.Decoded decoded =
                    OpaqueCursor.decode(cursor, PhotoErrorCode.INVALID_CURSOR);
            cursorCreatedAt = decoded.timestamp();
            cursorCommentId = decoded.id();
        }

        List<PhotoComment> fetched =
                photoCommentRepository.findPageByPhotoId(
                        photoId, cursorCreatedAt, cursorCommentId, PageRequest.of(0, pageSize + 1));
        boolean hasNext = fetched.size() > pageSize;
        List<PhotoComment> pageItems = hasNext ? fetched.subList(0, pageSize) : fetched;
        String nextCursor = null;
        if (hasNext) {
            PhotoComment last = pageItems.get(pageItems.size() - 1);
            nextCursor = OpaqueCursor.encode(last.getCreatedAt(), last.getId());
        }

        return new PhotoCommentListResponse(
                pageItems.stream().map(comment -> toCommentListItem(comment, appUserId)).toList(),
                nextCursor,
                hasNext);
    }

    @Transactional
    public PhotoCommentCreateResult createComment(
            UUID photoId,
            UUID appUserId,
            String idempotencyKeyHeader,
            PhotoCommentCreateRequest request) {
        Photo photo = requireActivePhoto(photoId);
        photoAccessGuard.requireActivePhotoAccess(photo, appUserId);
        String normalizedContent = normalizeCommentContent(request);
        UUID idempotencyKey = idempotencyService.parseIdempotencyKey(idempotencyKeyHeader);
        String apiPath = "/api/v1/photos/" + photoId + "/comments";
        IdempotencyService.IdempotencyStart<PhotoCommentResponse> idempotencyStart =
                idempotencyService.start(
                        COMMENT_CREATE_SCOPE_PREFIX + appUserId,
                        idempotencyKey,
                        HTTP_METHOD_POST,
                        apiPath,
                        idempotencyService.hashCanonicalRequest(
                                new PhotoCommentCreateRequest(normalizedContent)),
                        PhotoCommentResponse.class);
        if (idempotencyStart.replayed()) {
            return new PhotoCommentCreateResult(idempotencyStart.replayResponse(), true);
        }

        PhotoComment comment =
                photoCommentRepository.save(
                        PhotoComment.create(photo, appUserReference(appUserId), normalizedContent));
        PhotoCommentResponse response = toCommentResponse(comment);
        idempotencyService.complete(
                idempotencyStart.record(), ReactionSuccessCode.PHOTO_COMMENT_CREATED, response);
        return new PhotoCommentCreateResult(response, false);
    }

    private Photo requireActivePhoto(UUID photoId) {
        return photoRepository
                .findById(photoId)
                .filter(photo -> photo.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(PhotoErrorCode.PHOTO_NOT_FOUND));
    }

    private PresignedDownload issueThumbnailDownload(Photo photo) {
        if (photo.getThumbnailStatus() != PhotoThumbnailStatus.READY
                || photo.getThumbnailObjectKey() == null) {
            return null;
        }
        return objectStorageService.issueDownloadUrl(
                photo.getThumbnailObjectKey(), DOWNLOAD_URL_TTL);
    }

    private AppUser appUserReference(UUID appUserId) {
        return entityManager.getReference(AppUser.class, appUserId);
    }

    private String normalizeCommentContent(PhotoCommentCreateRequest request) {
        if (request == null || request.content() == null) {
            throw new BusinessException(ReactionErrorCode.INVALID_PHOTO_COMMENT_CONTENT);
        }
        String content = request.content().strip();
        if (content.isEmpty() || content.length() > MAX_COMMENT_CONTENT_LENGTH) {
            throw new BusinessException(ReactionErrorCode.INVALID_PHOTO_COMMENT_CONTENT);
        }
        return content;
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(MAX_PAGE_SIZE, size));
    }

    private Instant displayAtOf(Photo photo) {
        return photo.getTakenAt() == null ? photo.getCreatedAt() : photo.getTakenAt();
    }

    private PhotoCommentListResponse.Item toCommentListItem(PhotoComment comment, UUID appUserId) {
        AppUser author = comment.getAppUser();
        return new PhotoCommentListResponse.Item(
                comment.getId(),
                comment.getContent(),
                new PhotoCommentResponse.Author(author.getId(), author.getDisplayName()),
                author.getId().equals(appUserId),
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }

    private PhotoCommentResponse toCommentResponse(PhotoComment comment) {
        AppUser author = comment.getAppUser();
        return new PhotoCommentResponse(
                comment.getId(),
                comment.getPhoto().getId(),
                comment.getContent(),
                new PhotoCommentResponse.Author(author.getId(), author.getDisplayName()),
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }

    public record PhotoCommentCreateResult(PhotoCommentResponse response, boolean replayed) {}
}
