package org.zipzip.zipzipserver.domain.photo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbumPhoto;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumPhotoRepository;
import org.zipzip.zipzipserver.domain.photo.code.PhotoErrorCode;
import org.zipzip.zipzipserver.domain.photo.dto.request.PhotoIdsRequest;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoAttachResponse;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoBulkDeleteResponse;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoDetachResponse;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoMetadataUpdateResponse;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.photo.repository.PhotoRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.InviteCodeReservation;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMembershipRepository;
import org.zipzip.zipzipserver.domain.storage.ObjectStorageService;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.idempotency.ApiIdempotencyRecord;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;

@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

    @Mock private PhotoAccessGuard photoAccessGuard;
    @Mock private PhotoRepository photoRepository;
    @Mock private SharedAlbumPhotoRepository sharedAlbumPhotoRepository;
    @Mock private SharedGroupMembershipRepository sharedGroupMembershipRepository;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private IdempotencyService idempotencyService;

    @InjectMocks private PhotoService photoService;

    private AppUser uploader;
    private SharedGroup sharedGroup;
    private SharedAlbum sharedAlbum;

    @BeforeEach
    void setUp() {
        uploader = AppUser.create("apple-subject-" + UUID.randomUUID(), "업로더");
        sharedGroup =
                SharedGroup.create(
                        uploader, "그룹", InviteCodeReservation.create("CODE" + UUID.randomUUID()));
        sharedAlbum = SharedAlbum.create(sharedGroup, uploader, "앨범");
    }

    // updateMetadata

    @Test
    void 메타데이터_수정_업로더가_아니면_예외() {
        Photo photo = aPhoto(uploader);
        UUID requesterId = UUID.randomUUID();
        when(photoRepository.findById(photo.getId())).thenReturn(Optional.of(photo));

        assertThatThrownBy(
                        () ->
                                photoService.updateMetadata(
                                        photo.getId(),
                                        requesterId,
                                        Map.of("takenAt", "2026-06-30T04:20:00Z")))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(PhotoErrorCode.NOT_PHOTO_UPLOADER));
    }

    @Test
    void 메타데이터_수정_takenAt_형식이_잘못되면_예외() {
        Photo photo = aPhoto(uploader);
        when(photoRepository.findById(photo.getId())).thenReturn(Optional.of(photo));

        assertThatThrownBy(
                        () ->
                                photoService.updateMetadata(
                                        photo.getId(),
                                        uploader.getId(),
                                        Map.of("takenAt", "not-a-date")))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(PhotoErrorCode.INVALID_TAKEN_AT));
    }

    @Test
    void 메타데이터_수정_위치_필드를_일부만_전달하면_예외() {
        Photo photo = aPhoto(uploader);
        when(photoRepository.findById(photo.getId())).thenReturn(Optional.of(photo));

        assertThatThrownBy(
                        () ->
                                photoService.updateMetadata(
                                        photo.getId(), uploader.getId(), Map.of("latitude", 37.5)))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(PhotoErrorCode.INVALID_PHOTO_LOCATION));
    }

    @Test
    void 메타데이터_수정_정상_케이스면_takenAt과_위치가_반영된다() {
        Photo photo = aPhoto(uploader);
        when(photoRepository.findById(photo.getId())).thenReturn(Optional.of(photo));
        Map<String, Object> rawBody =
                Map.of(
                        "takenAt",
                        "2026-06-30T04:20:00Z",
                        "latitude",
                        37.5,
                        "longitude",
                        127.0,
                        "locationName",
                        "서울");

        PhotoMetadataUpdateResponse response =
                photoService.updateMetadata(photo.getId(), uploader.getId(), rawBody);

        assertThat(response.takenAt()).isEqualTo(Instant.parse("2026-06-30T04:20:00Z"));
        assertThat(response.latitude()).isEqualTo(37.5);
        assertThat(response.longitude()).isEqualTo(127.0);
        assertThat(response.locationName()).isEqualTo("서울");
        assertThat(photo.getTakenAt()).isEqualTo(Instant.parse("2026-06-30T04:20:00Z"));
    }

    // bulkDelete

    @Test
    void 사진_일괄삭제_정상_케이스면_모두_soft_delete되고_응답을_반환한다() {
        UUID albumId = UUID.randomUUID();
        UUID userId = uploader.getId();
        String idempotencyKeyHeader = UUID.randomUUID().toString();
        Photo photo1 = aPhoto(uploader);
        Photo photo2 = aPhoto(uploader);
        PhotoIdsRequest request = new PhotoIdsRequest(List.of(photo1.getId(), photo2.getId()));

        when(photoAccessGuard.requireActiveSharedAlbum(albumId, userId)).thenReturn(sharedAlbum);
        stubFreshIdempotency(
                "PHOTO_BULK_DELETE:",
                userId,
                idempotencyKeyHeader,
                request,
                PhotoBulkDeleteResponse.class);
        when(sharedAlbumPhotoRepository.existsBySharedAlbumIdAndPhotoId(albumId, photo1.getId()))
                .thenReturn(true);
        when(sharedAlbumPhotoRepository.existsBySharedAlbumIdAndPhotoId(albumId, photo2.getId()))
                .thenReturn(true);
        when(photoRepository.findById(photo1.getId())).thenReturn(Optional.of(photo1));
        when(photoRepository.findById(photo2.getId())).thenReturn(Optional.of(photo2));

        PhotoBulkDeleteResponse response =
                photoService.bulkDelete(albumId, userId, idempotencyKeyHeader, request);

        assertThat(response.deletedCount()).isEqualTo(2);
        assertThat(photo1.getDeletedAt()).isNotNull();
        assertThat(photo2.getDeletedAt()).isNotNull();
        verify(idempotencyService).complete(any(ApiIdempotencyRecord.class), any(), eq(response));
    }

    @Test
    void 사진_일괄삭제_공유집에_속하지_않은_사진이_있으면_예외() {
        UUID albumId = UUID.randomUUID();
        UUID userId = uploader.getId();
        String idempotencyKeyHeader = UUID.randomUUID().toString();
        Photo photo = aPhoto(uploader);
        PhotoIdsRequest request = new PhotoIdsRequest(List.of(photo.getId()));

        when(photoAccessGuard.requireActiveSharedAlbum(albumId, userId)).thenReturn(sharedAlbum);
        stubFreshIdempotency(
                "PHOTO_BULK_DELETE:",
                userId,
                idempotencyKeyHeader,
                request,
                PhotoBulkDeleteResponse.class);
        when(sharedAlbumPhotoRepository.existsBySharedAlbumIdAndPhotoId(albumId, photo.getId()))
                .thenReturn(false);

        assertThatThrownBy(
                        () ->
                                photoService.bulkDelete(
                                        albumId, userId, idempotencyKeyHeader, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(PhotoErrorCode.PHOTO_NOT_FOUND));
    }

    // attachPhotos

    @Test
    void 사진_추가_다른_공유그룹의_사진이면_예외() {
        UUID albumId = UUID.randomUUID();
        UUID userId = uploader.getId();
        String idempotencyKeyHeader = UUID.randomUUID().toString();
        Photo photo = aPhoto(uploader);
        PhotoIdsRequest request = new PhotoIdsRequest(List.of(photo.getId()));

        when(photoAccessGuard.requireActiveSharedAlbum(albumId, userId)).thenReturn(sharedAlbum);
        stubFreshIdempotency(
                "PHOTO_ATTACH:", userId, idempotencyKeyHeader, request, PhotoAttachResponse.class);
        when(photoRepository.findById(photo.getId())).thenReturn(Optional.of(photo));
        when(photoAccessGuard.resolveActiveSharedGroupId(photo)).thenReturn(UUID.randomUUID());

        assertThatThrownBy(
                        () ->
                                photoService.attachPhotos(
                                        albumId, userId, idempotencyKeyHeader, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(PhotoErrorCode.PHOTO_NOT_IN_SAME_SHARED_GROUP));
    }

    @Test
    void 사진_추가_이미_속해있으면_alreadyAttachedCount만_증가한다() {
        UUID albumId = UUID.randomUUID();
        UUID userId = uploader.getId();
        String idempotencyKeyHeader = UUID.randomUUID().toString();
        Photo photo = aPhoto(uploader);
        PhotoIdsRequest request = new PhotoIdsRequest(List.of(photo.getId()));

        when(photoAccessGuard.requireActiveSharedAlbum(albumId, userId)).thenReturn(sharedAlbum);
        stubFreshIdempotency(
                "PHOTO_ATTACH:", userId, idempotencyKeyHeader, request, PhotoAttachResponse.class);
        when(photoRepository.findById(photo.getId())).thenReturn(Optional.of(photo));
        when(photoAccessGuard.resolveActiveSharedGroupId(photo)).thenReturn(sharedGroup.getId());
        when(sharedAlbumPhotoRepository.existsBySharedAlbumIdAndPhotoId(albumId, photo.getId()))
                .thenReturn(true);

        PhotoAttachResponse response =
                photoService.attachPhotos(albumId, userId, idempotencyKeyHeader, request);

        assertThat(response.attachedCount()).isZero();
        assertThat(response.alreadyAttachedCount()).isEqualTo(1);
        verify(sharedAlbumPhotoRepository, never()).save(any());
    }

    @Test
    void 사진_추가_정상_케이스면_매핑을_저장하고_attachedCount가_증가한다() {
        UUID albumId = UUID.randomUUID();
        UUID userId = uploader.getId();
        String idempotencyKeyHeader = UUID.randomUUID().toString();
        Photo photo = aPhoto(uploader);
        PhotoIdsRequest request = new PhotoIdsRequest(List.of(photo.getId()));

        when(photoAccessGuard.requireActiveSharedAlbum(albumId, userId)).thenReturn(sharedAlbum);
        stubFreshIdempotency(
                "PHOTO_ATTACH:", userId, idempotencyKeyHeader, request, PhotoAttachResponse.class);
        when(photoRepository.findById(photo.getId())).thenReturn(Optional.of(photo));
        when(photoAccessGuard.resolveActiveSharedGroupId(photo)).thenReturn(sharedGroup.getId());
        when(sharedAlbumPhotoRepository.existsBySharedAlbumIdAndPhotoId(albumId, photo.getId()))
                .thenReturn(false);

        PhotoAttachResponse response =
                photoService.attachPhotos(albumId, userId, idempotencyKeyHeader, request);

        assertThat(response.attachedCount()).isEqualTo(1);
        assertThat(response.alreadyAttachedCount()).isZero();
        verify(sharedAlbumPhotoRepository).save(any(SharedAlbumPhoto.class));
    }

    // detachPhotos

    @Test
    void 사진_제거_매핑이_없으면_건너뛴다() {
        UUID albumId = UUID.randomUUID();
        UUID userId = uploader.getId();
        String idempotencyKeyHeader = UUID.randomUUID().toString();
        UUID photoId = UUID.randomUUID();
        PhotoIdsRequest request = new PhotoIdsRequest(List.of(photoId));

        when(photoAccessGuard.requireActiveSharedAlbum(albumId, userId)).thenReturn(sharedAlbum);
        stubFreshIdempotency(
                "PHOTO_DETACH:", userId, idempotencyKeyHeader, request, PhotoDetachResponse.class);
        when(sharedAlbumPhotoRepository.findBySharedAlbumIdAndPhotoId(albumId, photoId))
                .thenReturn(Optional.empty());

        PhotoDetachResponse response =
                photoService.detachPhotos(albumId, userId, idempotencyKeyHeader, request);

        assertThat(response.detachedCount()).isZero();
        assertThat(response.deletedPhotoCount()).isZero();
        verify(sharedAlbumPhotoRepository, never()).delete(any());
    }

    @Test
    void 사진_제거_마지막_매핑이면_사진도_soft_delete된다() {
        UUID albumId = UUID.randomUUID();
        UUID userId = uploader.getId();
        String idempotencyKeyHeader = UUID.randomUUID().toString();
        Photo photo = aPhoto(uploader);
        SharedAlbumPhoto mapping = SharedAlbumPhoto.create(sharedAlbum, photo);
        PhotoIdsRequest request = new PhotoIdsRequest(List.of(photo.getId()));

        when(photoAccessGuard.requireActiveSharedAlbum(albumId, userId)).thenReturn(sharedAlbum);
        stubFreshIdempotency(
                "PHOTO_DETACH:", userId, idempotencyKeyHeader, request, PhotoDetachResponse.class);
        when(sharedAlbumPhotoRepository.findBySharedAlbumIdAndPhotoId(albumId, photo.getId()))
                .thenReturn(Optional.of(mapping));
        when(sharedAlbumPhotoRepository.countByPhotoId(photo.getId())).thenReturn(0L);
        when(photoRepository.findById(photo.getId())).thenReturn(Optional.of(photo));

        PhotoDetachResponse response =
                photoService.detachPhotos(albumId, userId, idempotencyKeyHeader, request);

        assertThat(response.detachedCount()).isEqualTo(1);
        assertThat(response.deletedPhotoCount()).isEqualTo(1);
        assertThat(photo.getDeletedAt()).isNotNull();
        verify(sharedAlbumPhotoRepository).delete(mapping);
    }

    @Test
    void 사진_제거_다른_매핑이_남아있으면_사진은_삭제되지_않는다() {
        UUID albumId = UUID.randomUUID();
        UUID userId = uploader.getId();
        String idempotencyKeyHeader = UUID.randomUUID().toString();
        Photo photo = aPhoto(uploader);
        SharedAlbumPhoto mapping = SharedAlbumPhoto.create(sharedAlbum, photo);
        PhotoIdsRequest request = new PhotoIdsRequest(List.of(photo.getId()));

        when(photoAccessGuard.requireActiveSharedAlbum(albumId, userId)).thenReturn(sharedAlbum);
        stubFreshIdempotency(
                "PHOTO_DETACH:", userId, idempotencyKeyHeader, request, PhotoDetachResponse.class);
        when(sharedAlbumPhotoRepository.findBySharedAlbumIdAndPhotoId(albumId, photo.getId()))
                .thenReturn(Optional.of(mapping));
        when(sharedAlbumPhotoRepository.countByPhotoId(photo.getId())).thenReturn(1L);

        PhotoDetachResponse response =
                photoService.detachPhotos(albumId, userId, idempotencyKeyHeader, request);

        assertThat(response.detachedCount()).isEqualTo(1);
        assertThat(response.deletedPhotoCount()).isZero();
        assertThat(photo.getDeletedAt()).isNull();
    }

    private <T> void stubFreshIdempotency(
            String scopePrefix,
            UUID userId,
            String idempotencyKeyHeader,
            Object request,
            Class<T> responseType) {
        UUID idempotencyKey = UUID.fromString(idempotencyKeyHeader);
        when(idempotencyService.parseIdempotencyKey(idempotencyKeyHeader))
                .thenReturn(idempotencyKey);
        when(idempotencyService.hashCanonicalRequest(request)).thenReturn("hash");
        ApiIdempotencyRecord record =
                ApiIdempotencyRecord.processing(
                        scopePrefix + userId,
                        idempotencyKey,
                        "POST",
                        "path",
                        "hash",
                        Instant.now().plusSeconds(600));
        when(idempotencyService.start(
                        anyString(),
                        eq(idempotencyKey),
                        eq("POST"),
                        anyString(),
                        eq("hash"),
                        eq(responseType)))
                .thenReturn(new IdempotencyService.IdempotencyStart<>(record, null, false));
    }

    private AppUser anAppUser() {
        return AppUser.create("apple-subject-" + UUID.randomUUID(), "사용자");
    }

    private Photo aPhoto(AppUser photoUploader) {
        return Photo.create(
                photoUploader,
                "iPhone 15",
                "photos/" + UUID.randomUUID() + ".jpg",
                null,
                null,
                null);
    }
}
