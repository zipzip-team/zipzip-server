package org.zipzip.zipzipserver.domain.photo.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.photo.repository.PhotoRepository;
import org.zipzip.zipzipserver.domain.storage.ObjectKeyGenerator;
import org.zipzip.zipzipserver.domain.storage.ObjectStorageService;

/**
 * 원본을 재다운로드해 썸네일을 만드는 백그라운드 워커. 재시작으로 유실돼도 원본이 스토리지에 이미 있으므로 스윕이 재수거한다(PENDING/FAILED 재제출은
 * PhotoSweepScheduler 담당).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailProcessingService {

    private static final String THUMBNAIL_CONTENT_TYPE = "image/jpeg";

    private final PhotoRepository photoRepository;
    private final ObjectStorageService objectStorageService;
    private final ObjectKeyGenerator objectKeyGenerator;
    private final ThumbnailImageProcessor thumbnailImageProcessor;
    private final PhotoThumbnailStatusService photoThumbnailStatusService;

    @Async("thumbnailExecutor")
    public void process(UUID photoId) {
        Photo photo = photoRepository.findById(photoId).orElse(null);
        if (photo == null || photo.getDeletedAt() != null) {
            return;
        }

        try {
            byte[] original = objectStorageService.download(photo.getOriginalObjectKey());
            byte[] thumbnail = thumbnailImageProcessor.createThumbnail(original);
            String thumbnailKey = objectKeyGenerator.thumbnailKeyFor(photo.getOriginalObjectKey());
            objectStorageService.upload(thumbnailKey, thumbnail, THUMBNAIL_CONTENT_TYPE);
            photoThumbnailStatusService.markReady(photoId, thumbnailKey);
        } catch (Exception exception) {
            log.warn("[ThumbnailProcessing] photoId={} 썸네일 생성에 실패했습니다.", photoId, exception);
            photoThumbnailStatusService.markFailed(photoId);
        }
    }
}
