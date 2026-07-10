package org.zipzip.zipzipserver.domain.photo.scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.photo.entity.PhotoThumbnailStatus;
import org.zipzip.zipzipserver.domain.photo.entity.PhotoUploadReservation;
import org.zipzip.zipzipserver.domain.photo.repository.PhotoRepository;
import org.zipzip.zipzipserver.domain.photo.repository.PhotoUploadReservationRepository;
import org.zipzip.zipzipserver.domain.photo.service.PhotoPurgeService;
import org.zipzip.zipzipserver.domain.photo.service.ThumbnailProcessingService;
import org.zipzip.zipzipserver.domain.storage.ObjectStorageService;

/**
 * 재시작으로 인메모리 상태(예약, 진행 중 썸네일 작업)가 날아가도 원본은 이미 스토리지에 안전하게 있으므로, 주기적 스윕이 DB 상태만 보고 재수거·정리한다(4.3, 4.6
 * 참고). 공유 그룹/공유집(앨범)의 30일 유예 정리는 그 쓰기 API가 아직 없어 이번 범위에 포함하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhotoSweepScheduler {

    private static final Duration THUMBNAIL_STALE_THRESHOLD = Duration.ofMinutes(5);
    private static final Duration PHOTO_PURGE_GRACE_PERIOD = Duration.ofDays(30);

    private final PhotoRepository photoRepository;
    private final PhotoUploadReservationRepository photoUploadReservationRepository;
    private final ObjectStorageService objectStorageService;
    private final ThumbnailProcessingService thumbnailProcessingService;
    private final PhotoPurgeService photoPurgeService;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    @Scheduled(fixedDelayString = "${photo.sweep.reservation-cleanup-interval:PT5M}")
    public void sweepExpiredUploadReservations() {
        Instant now = Instant.now(clock);
        List<PhotoUploadReservation> expired =
                photoUploadReservationRepository.findByExpiresAtLessThanEqual(now);

        for (PhotoUploadReservation reservation : expired) {
            try {
                objectStorageService.delete(reservation.getObjectKey());
            } catch (Exception exception) {
                log.warn(
                        "[PhotoSweep] 만료 예약 객체 삭제 실패 objectKey={}",
                        reservation.getObjectKey(),
                        exception);
            }
        }
        photoUploadReservationRepository.deleteByExpiresAtLessThanEqual(now);
    }

    @Scheduled(fixedDelayString = "${photo.sweep.thumbnail-retry-interval:PT5M}")
    public void resubmitStaleThumbnailJobs() {
        Instant staleBefore = Instant.now(clock).minus(THUMBNAIL_STALE_THRESHOLD);
        List<Photo> staleJobs =
                photoRepository.findByDeletedAtIsNullAndThumbnailStatusInAndUpdatedAtLessThanEqual(
                        List.of(PhotoThumbnailStatus.PENDING, PhotoThumbnailStatus.FAILED),
                        staleBefore);
        staleJobs.forEach(photo -> thumbnailProcessingService.process(photo.getId()));
    }

    @Scheduled(fixedDelayString = "${photo.sweep.photo-purge-interval:PT1H}")
    public void purgeOldSoftDeletedPhotos() {
        Instant purgeBefore = Instant.now(clock).minus(PHOTO_PURGE_GRACE_PERIOD);
        List<Photo> purgeCandidates =
                photoRepository.findByDeletedAtIsNotNullAndDeletedAtLessThanEqual(purgeBefore);

        for (Photo photo : purgeCandidates) {
            try {
                photoPurgeService.purge(photo.getId());
            } catch (Exception exception) {
                log.warn("[PhotoSweep] 사진 물리 삭제 실패 photoId={}", photo.getId(), exception);
            }
        }
    }
}
