package org.zipzip.zipzipserver.domain.album.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.zipzip.zipzipserver.domain.album.code.SharedAlbumErrorCode;
import org.zipzip.zipzipserver.domain.album.dto.request.SharedAlbumIdsRequest;
import org.zipzip.zipzipserver.domain.album.dto.request.SharedAlbumNameRequest;
import org.zipzip.zipzipserver.domain.album.dto.response.SharedAlbumBulkDeleteResponse;
import org.zipzip.zipzipserver.domain.album.dto.response.SharedAlbumListResponse;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbumPhoto;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumPhotoRepository;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumRepository;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.photo.entity.PhotoThumbnailStatus;
import org.zipzip.zipzipserver.domain.photo.repository.PhotoRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.InviteCodeReservation;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.storage.ObjectStorageService;
import org.zipzip.zipzipserver.domain.storage.PresignedDownload;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.domain.user.repository.AppUserRepository;
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.idempotency.ApiIdempotencyRecord;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;

@ExtendWith(MockitoExtension.class)
class SharedAlbumServiceTest {

    @Mock private SharedAlbumAccessGuard sharedAlbumAccessGuard;
    @Mock private SharedAlbumRepository sharedAlbumRepository;
    @Mock private SharedAlbumPhotoRepository sharedAlbumPhotoRepository;
    @Mock private PhotoRepository photoRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private IdempotencyService idempotencyService;

    @InjectMocks private SharedAlbumService sharedAlbumService;

    @Test
    void 이름이_공백이면_이름_수정시_예외가_발생한다() {
        UUID albumId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(sharedAlbumAccessGuard.requireActiveSharedAlbum(albumId, requesterId))
                .thenReturn(anAlbum(anAppUser(), aSharedGroup(anAppUser())));

        assertThatThrownBy(
                        () ->
                                sharedAlbumService.renameAlbum(
                                        albumId, requesterId, new SharedAlbumNameRequest("   ")))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(SharedAlbumErrorCode.INVALID_SHARED_ALBUM_NAME));
    }

    @Test
    void 이름이_100자를_초과하면_이름_수정시_예외가_발생한다() {
        UUID albumId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(sharedAlbumAccessGuard.requireActiveSharedAlbum(albumId, requesterId))
                .thenReturn(anAlbum(anAppUser(), aSharedGroup(anAppUser())));
        String tooLongName = "이".repeat(101);

        assertThatThrownBy(
                        () ->
                                sharedAlbumService.renameAlbum(
                                        albumId,
                                        requesterId,
                                        new SharedAlbumNameRequest(tooLongName)))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(SharedAlbumErrorCode.INVALID_SHARED_ALBUM_NAME));
    }

    @Test
    void 생성자도_방장도_아닌_활성_멤버도_삭제할_수_있다() {
        AppUser creator = anAppUser();
        SharedGroup group = aSharedGroup(anAppUser());
        SharedAlbum album = anAlbum(creator, group);
        UUID requesterId = UUID.randomUUID();

        when(sharedAlbumAccessGuard.requireActiveSharedAlbum(album.getId(), requesterId))
                .thenReturn(album);
        when(sharedAlbumPhotoRepository.findBySharedAlbumId(album.getId())).thenReturn(List.of());

        sharedAlbumService.deleteAlbum(album.getId(), requesterId);

        assertThat(album.getDeletedAt()).isNotNull();
    }

    @Test
    void 삭제시_마지막_소속을_잃은_사진은_함께_soft_delete된다() {
        AppUser creator = anAppUser();
        SharedGroup group = aSharedGroup(creator);
        SharedAlbum album = anAlbum(creator, group);
        Photo lastMappingPhoto = aPhoto(creator);
        Photo stillAttachedPhoto = aPhoto(creator);

        when(sharedAlbumAccessGuard.requireActiveSharedAlbum(album.getId(), creator.getId()))
                .thenReturn(album);
        when(sharedAlbumPhotoRepository.findBySharedAlbumId(album.getId()))
                .thenReturn(
                        List.of(
                                SharedAlbumPhoto.create(album, lastMappingPhoto),
                                SharedAlbumPhoto.create(album, stillAttachedPhoto)));
        when(sharedAlbumPhotoRepository.countByPhotoId(lastMappingPhoto.getId())).thenReturn(0L);
        when(sharedAlbumPhotoRepository.countByPhotoId(stillAttachedPhoto.getId())).thenReturn(1L);
        when(photoRepository.findById(lastMappingPhoto.getId()))
                .thenReturn(Optional.of(lastMappingPhoto));

        sharedAlbumService.deleteAlbum(album.getId(), creator.getId());

        assertThat(lastMappingPhoto.getDeletedAt()).isNotNull();
        assertThat(album.getDeletedAt()).isNotNull();
    }

    @Test
    void 일괄_삭제_요청이_비었으면_예외가_발생한다() {
        UUID requesterId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                sharedAlbumService.bulkDeleteAlbums(
                                        requesterId,
                                        UUID.randomUUID().toString(),
                                        new SharedAlbumIdsRequest(List.of())))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(SharedAlbumErrorCode.INVALID_SHARED_ALBUM_IDS));
    }

    @Test
    void 일괄_삭제_요청에_중복_id가_있으면_예외가_발생한다() {
        UUID requesterId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                sharedAlbumService.bulkDeleteAlbums(
                                        requesterId,
                                        UUID.randomUUID().toString(),
                                        new SharedAlbumIdsRequest(List.of(albumId, albumId))))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(SharedAlbumErrorCode.INVALID_SHARED_ALBUM_IDS));
    }

    @Test
    void 일괄_삭제_요청_본문이_null이면_예외가_발생한다() {
        UUID requesterId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                sharedAlbumService.bulkDeleteAlbums(
                                        requesterId, UUID.randomUUID().toString(), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(SharedAlbumErrorCode.INVALID_SHARED_ALBUM_IDS));
    }

    @Test
    void 일괄_삭제_요청에_null_id가_포함되면_예외가_발생한다() {
        UUID requesterId = UUID.randomUUID();
        UUID albumId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                sharedAlbumService.bulkDeleteAlbums(
                                        requesterId,
                                        UUID.randomUUID().toString(),
                                        new SharedAlbumIdsRequest(Arrays.asList(albumId, null))))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(SharedAlbumErrorCode.INVALID_SHARED_ALBUM_IDS));
    }

    @Test
    void 일괄_삭제_재요청은_저장된_응답을_그대로_반환하고_아무것도_다시_삭제하지_않는다() {
        UUID requesterId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        String idempotencyKeyHeader = idempotencyKey.toString();
        SharedAlbumIdsRequest request = new SharedAlbumIdsRequest(List.of(UUID.randomUUID()));
        SharedAlbumBulkDeleteResponse cachedResponse = new SharedAlbumBulkDeleteResponse(1, 0);

        when(idempotencyService.parseIdempotencyKey(idempotencyKeyHeader))
                .thenReturn(idempotencyKey);
        when(idempotencyService.hashCanonicalRequest(request)).thenReturn("hash");
        when(idempotencyService.start(
                        anyString(),
                        eq(idempotencyKey),
                        eq("POST"),
                        anyString(),
                        eq("hash"),
                        eq(SharedAlbumBulkDeleteResponse.class)))
                .thenReturn(new IdempotencyService.IdempotencyStart<>(null, cachedResponse, true));

        SharedAlbumBulkDeleteResponse response =
                sharedAlbumService.bulkDeleteAlbums(requesterId, idempotencyKeyHeader, request);

        assertThat(response).isSameAs(cachedResponse);
        verify(sharedAlbumAccessGuard, never()).requireActiveSharedAlbum(any(), any());
    }

    @Test
    void 대상_중_하나라도_존재하지_않으면_전체_요청이_실패하고_아무것도_삭제되지_않는다() {
        AppUser creator = anAppUser();
        SharedGroup group = aSharedGroup(creator);
        SharedAlbum existingAlbum = anAlbum(creator, group);
        UUID missingAlbumId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        String idempotencyKeyHeader = UUID.randomUUID().toString();
        SharedAlbumIdsRequest request =
                new SharedAlbumIdsRequest(List.of(existingAlbum.getId(), missingAlbumId));

        stubFreshIdempotency(
                "SHARED_ALBUM_BULK_DELETE:",
                requesterId,
                idempotencyKeyHeader,
                request,
                SharedAlbumBulkDeleteResponse.class);
        when(sharedAlbumAccessGuard.requireActiveSharedAlbum(existingAlbum.getId(), requesterId))
                .thenReturn(existingAlbum);
        when(sharedAlbumAccessGuard.requireActiveSharedAlbum(missingAlbumId, requesterId))
                .thenThrow(new BusinessException(SharedAlbumErrorCode.SHARED_ALBUM_NOT_FOUND));

        assertThatThrownBy(
                        () ->
                                sharedAlbumService.bulkDeleteAlbums(
                                        requesterId, idempotencyKeyHeader, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(SharedAlbumErrorCode.SHARED_ALBUM_NOT_FOUND));

        verify(sharedAlbumPhotoRepository, never()).deleteBySharedAlbumId(any());
        assertThat(existingAlbum.getDeletedAt()).isNull();
    }

    @Test
    void 선택한_공유집을_일괄_삭제하고_마지막_소속을_잃은_사진도_함께_삭제한다() {
        AppUser creator = anAppUser();
        SharedGroup group = aSharedGroup(creator);
        SharedAlbum albumA = anAlbum(creator, group);
        SharedAlbum albumB = anAlbum(creator, group);
        Photo photoInBothTargets = aPhoto(creator);
        Photo photoStillElsewhere = aPhoto(creator);
        UUID requesterId = UUID.randomUUID();
        String idempotencyKeyHeader = UUID.randomUUID().toString();
        SharedAlbumIdsRequest request =
                new SharedAlbumIdsRequest(List.of(albumA.getId(), albumB.getId()));

        stubFreshIdempotency(
                "SHARED_ALBUM_BULK_DELETE:",
                requesterId,
                idempotencyKeyHeader,
                request,
                SharedAlbumBulkDeleteResponse.class);
        when(sharedAlbumAccessGuard.requireActiveSharedAlbum(albumA.getId(), requesterId))
                .thenReturn(albumA);
        when(sharedAlbumAccessGuard.requireActiveSharedAlbum(albumB.getId(), requesterId))
                .thenReturn(albumB);
        when(sharedAlbumPhotoRepository.findBySharedAlbumId(albumA.getId()))
                .thenReturn(
                        List.of(
                                SharedAlbumPhoto.create(albumA, photoInBothTargets),
                                SharedAlbumPhoto.create(albumA, photoStillElsewhere)));
        when(sharedAlbumPhotoRepository.findBySharedAlbumId(albumB.getId()))
                .thenReturn(List.of(SharedAlbumPhoto.create(albumB, photoInBothTargets)));
        when(sharedAlbumPhotoRepository.countByPhotoId(photoInBothTargets.getId())).thenReturn(0L);
        when(sharedAlbumPhotoRepository.countByPhotoId(photoStillElsewhere.getId())).thenReturn(1L);
        when(photoRepository.findById(photoInBothTargets.getId()))
                .thenReturn(Optional.of(photoInBothTargets));

        SharedAlbumBulkDeleteResponse response =
                sharedAlbumService.bulkDeleteAlbums(requesterId, idempotencyKeyHeader, request);

        assertThat(response.deletedAlbumCount()).isEqualTo(2);
        assertThat(response.deletedPhotoCount()).isEqualTo(1);
        assertThat(albumA.getDeletedAt()).isNotNull();
        assertThat(albumB.getDeletedAt()).isNotNull();
        assertThat(photoInBothTargets.getDeletedAt()).isNotNull();
        assertThat(photoStillElsewhere.getDeletedAt()).isNull();
        verify(sharedAlbumPhotoRepository).deleteBySharedAlbumId(albumA.getId());
        verify(sharedAlbumPhotoRepository).deleteBySharedAlbumId(albumB.getId());
    }

    @Test
    void 공유집_목록_조회시_가장_먼저_저장된_사진_순으로_최대_3장의_썸네일을_반환한다() {
        AppUser creator = anAppUser();
        SharedGroup group = aSharedGroup(creator);
        SharedAlbum album = anAlbum(creator, group);
        UUID requesterId = creator.getId();
        Photo oldest = aReadyPhoto(creator, "thumb/oldest.jpg");
        Photo middle = aReadyPhoto(creator, "thumb/middle.jpg");
        Photo newest = aReadyPhoto(creator, "thumb/newest.jpg");

        when(sharedAlbumAccessGuard.requireActiveSharedGroup(group.getId(), requesterId))
                .thenReturn(group);
        when(sharedAlbumRepository.findPageByActiveSharedGroupId(
                        eq(group.getId()), any(), any(), any()))
                .thenReturn(List.of(album));
        when(sharedAlbumPhotoRepository.countBySharedAlbumIdAndPhotoDeletedAtIsNull(album.getId()))
                .thenReturn(3L);
        when(sharedAlbumPhotoRepository.findOldestPhotosBySharedAlbumId(eq(album.getId()), any()))
                .thenReturn(List.of(oldest, middle, newest));
        when(objectStorageService.issueDownloadUrl(eq("thumb/oldest.jpg"), any()))
                .thenReturn(new PresignedDownload("https://cdn/oldest", Instant.now()));
        when(objectStorageService.issueDownloadUrl(eq("thumb/middle.jpg"), any()))
                .thenReturn(new PresignedDownload("https://cdn/middle", Instant.now()));
        when(objectStorageService.issueDownloadUrl(eq("thumb/newest.jpg"), any()))
                .thenReturn(new PresignedDownload("https://cdn/newest", Instant.now()));

        SharedAlbumListResponse response =
                sharedAlbumService.listAlbums(group.getId(), requesterId, null, null);

        assertThat(response.items().get(0).thumbnails())
                .extracting(SharedAlbumListResponse.Thumbnail::url)
                .containsExactly("https://cdn/oldest", "https://cdn/middle", "https://cdn/newest");
    }

    @Test
    void 썸네일이_준비되지_않은_사진은_목록에서_제외된다() {
        AppUser creator = anAppUser();
        SharedGroup group = aSharedGroup(creator);
        SharedAlbum album = anAlbum(creator, group);
        UUID requesterId = creator.getId();
        Photo pending = aPhoto(creator);
        Photo ready = aReadyPhoto(creator, "thumb/ready.jpg");

        when(sharedAlbumAccessGuard.requireActiveSharedGroup(group.getId(), requesterId))
                .thenReturn(group);
        when(sharedAlbumRepository.findPageByActiveSharedGroupId(
                        eq(group.getId()), any(), any(), any()))
                .thenReturn(List.of(album));
        when(sharedAlbumPhotoRepository.countBySharedAlbumIdAndPhotoDeletedAtIsNull(album.getId()))
                .thenReturn(2L);
        when(sharedAlbumPhotoRepository.findOldestPhotosBySharedAlbumId(eq(album.getId()), any()))
                .thenReturn(List.of(pending, ready));
        when(objectStorageService.issueDownloadUrl(eq("thumb/ready.jpg"), any()))
                .thenReturn(new PresignedDownload("https://cdn/ready", Instant.now()));

        SharedAlbumListResponse response =
                sharedAlbumService.listAlbums(group.getId(), requesterId, null, null);

        assertThat(response.items().get(0).thumbnails())
                .extracting(SharedAlbumListResponse.Thumbnail::url)
                .containsExactly("https://cdn/ready");
    }

    @Test
    void 공백_키를_가진_기존_READY_썸네일은_목록에서_제외된다() {
        AppUser creator = anAppUser();
        SharedGroup group = aSharedGroup(creator);
        SharedAlbum album = anAlbum(creator, group);
        Photo malformed = aPhoto(creator);
        ReflectionTestUtils.setField(malformed, "thumbnailStatus", PhotoThumbnailStatus.READY);
        ReflectionTestUtils.setField(malformed, "thumbnailObjectKey", " \t");

        when(sharedAlbumAccessGuard.requireActiveSharedGroup(group.getId(), creator.getId()))
                .thenReturn(group);
        when(sharedAlbumRepository.findPageByActiveSharedGroupId(
                        eq(group.getId()), any(), any(), any()))
                .thenReturn(List.of(album));
        when(sharedAlbumPhotoRepository.countBySharedAlbumIdAndPhotoDeletedAtIsNull(album.getId()))
                .thenReturn(1L);
        when(sharedAlbumPhotoRepository.findOldestPhotosBySharedAlbumId(eq(album.getId()), any()))
                .thenReturn(List.of(malformed));

        SharedAlbumListResponse response =
                sharedAlbumService.listAlbums(group.getId(), creator.getId(), null, null);

        assertThat(response.items().getFirst().thumbnails()).isEmpty();
        verify(objectStorageService, never()).issueDownloadUrl(eq(" \t"), any());
    }

    @Test
    void 사진이_없는_공유집은_빈_썸네일_목록을_반환한다() {
        AppUser creator = anAppUser();
        SharedGroup group = aSharedGroup(creator);
        SharedAlbum album = anAlbum(creator, group);
        UUID requesterId = creator.getId();

        when(sharedAlbumAccessGuard.requireActiveSharedGroup(group.getId(), requesterId))
                .thenReturn(group);
        when(sharedAlbumRepository.findPageByActiveSharedGroupId(
                        eq(group.getId()), any(), any(), any()))
                .thenReturn(List.of(album));
        when(sharedAlbumPhotoRepository.countBySharedAlbumIdAndPhotoDeletedAtIsNull(album.getId()))
                .thenReturn(0L);
        when(sharedAlbumPhotoRepository.findOldestPhotosBySharedAlbumId(eq(album.getId()), any()))
                .thenReturn(List.of());

        SharedAlbumListResponse response =
                sharedAlbumService.listAlbums(group.getId(), requesterId, null, null);

        assertThat(response.items().get(0).thumbnails()).isEmpty();
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

    private SharedGroup aSharedGroup(AppUser creator) {
        return SharedGroup.create(
                creator, "그룹", InviteCodeReservation.create("CODE" + UUID.randomUUID()));
    }

    private SharedAlbum anAlbum(AppUser creator, SharedGroup group) {
        return SharedAlbum.create(group, creator, "앨범");
    }

    private Photo aPhoto(AppUser uploader) {
        return Photo.create(uploader, "iPhone 15", "photos/original.jpg", null, null, null);
    }

    private Photo aReadyPhoto(AppUser uploader, String thumbnailObjectKey) {
        Photo photo = aPhoto(uploader);
        photo.markThumbnailReady(thumbnailObjectKey);
        return photo;
    }
}
