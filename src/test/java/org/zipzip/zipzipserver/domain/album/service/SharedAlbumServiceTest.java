package org.zipzip.zipzipserver.domain.album.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zipzip.zipzipserver.domain.album.code.SharedAlbumErrorCode;
import org.zipzip.zipzipserver.domain.album.dto.request.SharedAlbumNameRequest;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbumPhoto;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumPhotoRepository;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumRepository;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.photo.repository.PhotoRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.InviteCodeReservation;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.domain.user.repository.AppUserRepository;
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;

@ExtendWith(MockitoExtension.class)
class SharedAlbumServiceTest {

    @Mock private SharedAlbumAccessGuard sharedAlbumAccessGuard;
    @Mock private SharedAlbumRepository sharedAlbumRepository;
    @Mock private SharedAlbumPhotoRepository sharedAlbumPhotoRepository;
    @Mock private PhotoRepository photoRepository;
    @Mock private AppUserRepository appUserRepository;
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
}
