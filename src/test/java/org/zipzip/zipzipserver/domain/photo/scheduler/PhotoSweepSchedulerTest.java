package org.zipzip.zipzipserver.domain.photo.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.photo.entity.PhotoThumbnailStatus;
import org.zipzip.zipzipserver.domain.photo.repository.PhotoRepository;
import org.zipzip.zipzipserver.domain.photo.repository.PhotoUploadReservationRepository;
import org.zipzip.zipzipserver.domain.photo.service.PhotoPurgeService;
import org.zipzip.zipzipserver.domain.photo.service.ThumbnailProcessingService;
import org.zipzip.zipzipserver.domain.storage.ObjectStorageService;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;

@ExtendWith(MockitoExtension.class)
class PhotoSweepSchedulerTest {

    @Mock private PhotoRepository photoRepository;
    @Mock private PhotoUploadReservationRepository photoUploadReservationRepository;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private ThumbnailProcessingService thumbnailProcessingService;
    @Mock private PhotoPurgeService photoPurgeService;

    @InjectMocks private PhotoSweepScheduler photoSweepScheduler;

    @Test
    void 썸네일_재제출_한_사진의_큐가_가득_차도_나머지_사진은_계속_재제출한다() {
        AppUser uploader = AppUser.create("apple-subject-" + UUID.randomUUID(), "업로더");
        Photo rejectedPhoto = staleThumbnailPhoto(uploader);
        Photo recoveredPhoto = staleThumbnailPhoto(uploader);
        when(photoRepository.findByDeletedAtIsNullAndThumbnailStatusInAndUpdatedAtLessThanEqual(
                        eq(List.of(PhotoThumbnailStatus.PENDING, PhotoThumbnailStatus.FAILED)),
                        any(Instant.class)))
                .thenReturn(List.of(rejectedPhoto, recoveredPhoto));
        doThrow(new TaskRejectedException("thumbnailExecutor 큐가 가득 찼습니다"))
                .when(thumbnailProcessingService)
                .process(rejectedPhoto.getId());

        photoSweepScheduler.resubmitStaleThumbnailJobs();

        verify(thumbnailProcessingService).process(rejectedPhoto.getId());
        verify(thumbnailProcessingService).process(recoveredPhoto.getId());
    }

    private Photo staleThumbnailPhoto(AppUser uploader) {
        return Photo.create(
                uploader, "iPhone 15", "photos/" + UUID.randomUUID() + ".jpg", null, null, null);
    }
}
