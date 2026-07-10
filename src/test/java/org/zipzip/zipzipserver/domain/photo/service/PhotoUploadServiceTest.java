package org.zipzip.zipzipserver.domain.photo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumPhotoRepository;
import org.zipzip.zipzipserver.domain.photo.code.PhotoErrorCode;
import org.zipzip.zipzipserver.domain.photo.dto.request.PhotoUploadCompleteRequest;
import org.zipzip.zipzipserver.domain.photo.dto.request.PhotoUploadUrlRequest;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoUploadCompleteResponse;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoUploadUrlResponse;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.photo.entity.PhotoUploadReservation;
import org.zipzip.zipzipserver.domain.photo.repository.PhotoRepository;
import org.zipzip.zipzipserver.domain.photo.repository.PhotoUploadReservationRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.InviteCodeReservation;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.storage.ObjectKeyGenerator;
import org.zipzip.zipzipserver.domain.storage.ObjectStorageService;
import org.zipzip.zipzipserver.domain.storage.PresignedDownload;
import org.zipzip.zipzipserver.domain.storage.PresignedUpload;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.domain.user.repository.AppUserRepository;
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.idempotency.ApiIdempotencyRecord;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;

@ExtendWith(MockitoExtension.class)
class PhotoUploadServiceTest {

    @Mock private PhotoAccessGuard photoAccessGuard;
    @Mock private PhotoRepository photoRepository;
    @Mock private PhotoUploadReservationRepository photoUploadReservationRepository;
    @Mock private SharedAlbumPhotoRepository sharedAlbumPhotoRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private ObjectKeyGenerator objectKeyGenerator;
    @Mock private IdempotencyService idempotencyService;
    @Mock private ThumbnailProcessingService thumbnailProcessingService;

    @InjectMocks private PhotoUploadService photoUploadService;

    private AppUser uploader;
    private SharedAlbum sharedAlbum;

    @BeforeEach
    void setUp() {
        // completeUpload는 커밋 후 썸네일 작업을 등록하므로(TransactionSynchronizationManager),
        // 실제 트랜잭션 없이도 등록이 가능하도록 동기화 컨텍스트를 열어둔다.
        TransactionSynchronizationManager.initSynchronization();
        uploader = AppUser.create("apple-subject-" + UUID.randomUUID(), "업로더");
        SharedGroup group =
                SharedGroup.create(
                        uploader, "그룹", InviteCodeReservation.create("CODE" + UUID.randomUUID()));
        sharedAlbum = SharedAlbum.create(group, uploader, "앨범");
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void 업로드_URL_발급_파일_목록이_비어있으면_예외() {
        UUID albumId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(photoAccessGuard.requireActiveSharedAlbum(albumId, userId)).thenReturn(sharedAlbum);

        assertThatThrownBy(
                        () ->
                                photoUploadService.issueUploadUrls(
                                        albumId, userId, new PhotoUploadUrlRequest(List.of())))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(PhotoErrorCode.INVALID_UPLOAD_METADATA));
    }

    @Test
    void 업로드_URL_발급_파일이_20개를_초과하면_예외() {
        UUID albumId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(photoAccessGuard.requireActiveSharedAlbum(albumId, userId)).thenReturn(sharedAlbum);
        List<PhotoUploadUrlRequest.UploadUrlFileSpec> files = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            files.add(new PhotoUploadUrlRequest.UploadUrlFileSpec("image/jpeg", 1_000L));
        }

        assertThatThrownBy(
                        () ->
                                photoUploadService.issueUploadUrls(
                                        albumId, userId, new PhotoUploadUrlRequest(files)))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(PhotoErrorCode.TOO_MANY_FILES));
    }

    @Test
    void 업로드_URL_발급_파일_크기가_20MiB를_초과하면_예외() {
        UUID albumId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(photoAccessGuard.requireActiveSharedAlbum(albumId, userId)).thenReturn(sharedAlbum);
        List<PhotoUploadUrlRequest.UploadUrlFileSpec> files =
                List.of(
                        new PhotoUploadUrlRequest.UploadUrlFileSpec(
                                "image/jpeg", 21L * 1024 * 1024));

        assertThatThrownBy(
                        () ->
                                photoUploadService.issueUploadUrls(
                                        albumId, userId, new PhotoUploadUrlRequest(files)))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(PhotoErrorCode.FILE_TOO_LARGE));
    }

    @Test
    void 업로드_URL_발급_이미지가_아닌_타입이면_예외() {
        UUID albumId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(photoAccessGuard.requireActiveSharedAlbum(albumId, userId)).thenReturn(sharedAlbum);
        List<PhotoUploadUrlRequest.UploadUrlFileSpec> files =
                List.of(new PhotoUploadUrlRequest.UploadUrlFileSpec("application/pdf", 1_000L));

        assertThatThrownBy(
                        () ->
                                photoUploadService.issueUploadUrls(
                                        albumId, userId, new PhotoUploadUrlRequest(files)))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(PhotoErrorCode.UNSUPPORTED_IMAGE_TYPE));
    }

    @Test
    void 업로드_URL_발급_정상_케이스() {
        UUID albumId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(photoAccessGuard.requireActiveSharedAlbum(albumId, userId)).thenReturn(sharedAlbum);
        when(appUserRepository.getReferenceById(userId)).thenReturn(uploader);
        when(objectKeyGenerator.generateOriginalKey("image/jpeg"))
                .thenReturn("photos/2026/07/10/object-key.jpg");
        when(objectStorageService.issueUploadUrl(
                        eq("photos/2026/07/10/object-key.jpg"),
                        eq("image/jpeg"),
                        eq(1_000L),
                        any(Duration.class)))
                .thenReturn(
                        new PresignedUpload("https://upload-url", Instant.now().plusSeconds(900)));

        PhotoUploadUrlResponse response =
                photoUploadService.issueUploadUrls(
                        albumId,
                        userId,
                        new PhotoUploadUrlRequest(
                                List.of(
                                        new PhotoUploadUrlRequest.UploadUrlFileSpec(
                                                "image/jpeg", 1_000L))));

        assertThat(response.uploads()).hasSize(1);
        assertThat(response.uploads().get(0).objectKey())
                .isEqualTo("photos/2026/07/10/object-key.jpg");
        assertThat(response.uploads().get(0).uploadUrl()).isEqualTo("https://upload-url");
        verify(photoUploadReservationRepository).save(any(PhotoUploadReservation.class));
    }

    @Test
    void 완료등록_멱등키_재시도면_기존_응답을_그대로_반환한다() {
        UUID albumId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKeyHeader = UUID.randomUUID().toString();
        UUID idempotencyKey = UUID.fromString(idempotencyKeyHeader);
        PhotoUploadCompleteRequest request =
                new PhotoUploadCompleteRequest(
                        List.of(
                                new PhotoUploadCompleteRequest.CompleteFileSpec(
                                        "object-key",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)));

        when(photoAccessGuard.requireActiveSharedAlbum(albumId, userId)).thenReturn(sharedAlbum);
        when(idempotencyService.parseIdempotencyKey(idempotencyKeyHeader))
                .thenReturn(idempotencyKey);
        when(idempotencyService.hashCanonicalRequest(request)).thenReturn("hash");
        PhotoUploadCompleteResponse cachedResponse = new PhotoUploadCompleteResponse(List.of());
        when(idempotencyService.start(
                        anyString(),
                        eq(idempotencyKey),
                        eq("POST"),
                        anyString(),
                        eq("hash"),
                        eq(PhotoUploadCompleteResponse.class)))
                .thenReturn(new IdempotencyService.IdempotencyStart<>(null, cachedResponse, true));

        PhotoUploadService.PhotoUploadCompleteResult result =
                photoUploadService.completeUpload(albumId, userId, idempotencyKeyHeader, request);

        assertThat(result.replayed()).isTrue();
        assertThat(result.response()).isSameAs(cachedResponse);
        verify(photoUploadReservationRepository, never()).findById(anyString());
    }

    @Test
    void 완료등록_예약이_없으면_UPLOAD_OBJECT_NOT_FOUND() {
        UUID albumId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKeyHeader = UUID.randomUUID().toString();
        PhotoUploadCompleteRequest request = completeRequestFor("object-key");

        givenFreshIdempotencyStart(albumId, userId, idempotencyKeyHeader, request);
        when(photoUploadReservationRepository.findById("object-key")).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                photoUploadService.completeUpload(
                                        albumId, userId, idempotencyKeyHeader, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(PhotoErrorCode.UPLOAD_OBJECT_NOT_FOUND));
    }

    @Test
    void 완료등록_예약이_만료되었으면_UPLOAD_OBJECT_NOT_FOUND() {
        UUID albumId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKeyHeader = UUID.randomUUID().toString();
        PhotoUploadCompleteRequest request = completeRequestFor("object-key");

        givenFreshIdempotencyStart(albumId, userId, idempotencyKeyHeader, request);
        when(appUserRepository.getReferenceById(userId)).thenReturn(uploader);
        PhotoUploadReservation expiredReservation =
                PhotoUploadReservation.create(
                        "object-key", sharedAlbum, uploader, Instant.now().minusSeconds(60));
        when(photoUploadReservationRepository.findById("object-key"))
                .thenReturn(Optional.of(expiredReservation));

        assertThatThrownBy(
                        () ->
                                photoUploadService.completeUpload(
                                        albumId, userId, idempotencyKeyHeader, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(PhotoErrorCode.UPLOAD_OBJECT_NOT_FOUND));
    }

    @Test
    void 완료등록_스토리지에_업로드가_안되어있으면_UPLOAD_NOT_COMPLETED() {
        UUID albumId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKeyHeader = UUID.randomUUID().toString();
        PhotoUploadCompleteRequest request = completeRequestFor("object-key");

        givenFreshIdempotencyStart(albumId, userId, idempotencyKeyHeader, request);
        when(appUserRepository.getReferenceById(userId)).thenReturn(uploader);
        PhotoUploadReservation usableReservation =
                PhotoUploadReservation.create(
                        "object-key", sharedAlbum, uploader, Instant.now().plusSeconds(900));
        when(photoUploadReservationRepository.findById("object-key"))
                .thenReturn(Optional.of(usableReservation));
        when(objectStorageService.exists("object-key")).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                photoUploadService.completeUpload(
                                        albumId, userId, idempotencyKeyHeader, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(PhotoErrorCode.UPLOAD_NOT_COMPLETED));
    }

    @Test
    void 완료등록_정상_케이스면_사진을_생성하고_앨범에_매핑하고_예약을_삭제한다() {
        UUID albumId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKeyHeader = UUID.randomUUID().toString();
        PhotoUploadCompleteRequest request = completeRequestFor("object-key");

        givenFreshIdempotencyStart(albumId, userId, idempotencyKeyHeader, request);
        when(appUserRepository.getReferenceById(userId)).thenReturn(uploader);
        PhotoUploadReservation usableReservation =
                PhotoUploadReservation.create(
                        "object-key", sharedAlbum, uploader, Instant.now().plusSeconds(900));
        when(photoUploadReservationRepository.findById("object-key"))
                .thenReturn(Optional.of(usableReservation));
        when(objectStorageService.exists("object-key")).thenReturn(true);
        when(photoRepository.save(any(Photo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(objectStorageService.issueDownloadUrl(eq("object-key"), any(Duration.class)))
                .thenReturn(
                        new PresignedDownload(
                                "https://original-url", Instant.now().plusSeconds(600)));

        PhotoUploadService.PhotoUploadCompleteResult result =
                photoUploadService.completeUpload(albumId, userId, idempotencyKeyHeader, request);

        assertThat(result.replayed()).isFalse();
        assertThat(result.response().items()).hasSize(1);
        assertThat(result.response().items().get(0).originalUrl())
                .isEqualTo("https://original-url");
        verify(sharedAlbumPhotoRepository).save(any());
        verify(photoUploadReservationRepository).delete(usableReservation);
        verify(idempotencyService)
                .complete(any(ApiIdempotencyRecord.class), any(), eq(result.response()));
    }

    private PhotoUploadCompleteRequest completeRequestFor(String objectKey) {
        return new PhotoUploadCompleteRequest(
                List.of(
                        new PhotoUploadCompleteRequest.CompleteFileSpec(
                                objectKey, "iPhone 15", null, null, null, null, null, null, null)));
    }

    private void givenFreshIdempotencyStart(
            UUID albumId,
            UUID userId,
            String idempotencyKeyHeader,
            PhotoUploadCompleteRequest request) {
        UUID idempotencyKey = UUID.fromString(idempotencyKeyHeader);
        when(photoAccessGuard.requireActiveSharedAlbum(albumId, userId)).thenReturn(sharedAlbum);
        when(idempotencyService.parseIdempotencyKey(idempotencyKeyHeader))
                .thenReturn(idempotencyKey);
        when(idempotencyService.hashCanonicalRequest(request)).thenReturn("hash");
        ApiIdempotencyRecord record =
                ApiIdempotencyRecord.processing(
                        "PHOTO_UPLOAD_COMPLETE:" + userId,
                        idempotencyKey,
                        "POST",
                        "/api/v1/shared-albums/" + albumId + "/photos/complete",
                        "hash",
                        Instant.now().plusSeconds(600));
        when(idempotencyService.start(
                        anyString(),
                        eq(idempotencyKey),
                        eq("POST"),
                        anyString(),
                        eq("hash"),
                        eq(PhotoUploadCompleteResponse.class)))
                .thenReturn(new IdempotencyService.IdempotencyStart<>(record, null, false));
    }
}
