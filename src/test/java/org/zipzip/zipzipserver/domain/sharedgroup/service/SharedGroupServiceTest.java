package org.zipzip.zipzipserver.domain.sharedgroup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumPhotoRepository;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumRepository;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupErrorCode;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.request.CreateSharedGroupRequest;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.CreateSharedGroupResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupUpdateResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.InviteCodeReservation;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupMembership;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.InviteCodeReservationRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMembershipRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupQueryRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupRepository;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.domain.user.repository.AppUserRepository;
import org.zipzip.zipzipserver.global.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class SharedGroupServiceTest {

    private static final UUID APP_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private AppUserRepository appUserRepository;
    @Mock private InviteCodeReservationRepository inviteCodeReservationRepository;
    @Mock private SharedGroupRepository sharedGroupRepository;
    @Mock private SharedGroupMembershipRepository sharedGroupMembershipRepository;
    @Mock private SharedGroupQueryRepository sharedGroupQueryRepository;
    @Mock private SharedAlbumRepository sharedAlbumRepository;
    @Mock private SharedAlbumPhotoRepository sharedAlbumPhotoRepository;
    @Mock private SharedGroupCursorCodec sharedGroupCursorCodec;
    @Spy private SharedGroupNameValidator sharedGroupNameValidator = new SharedGroupNameValidator();
    @Mock private InviteCodeGenerator inviteCodeGenerator;
    @Mock private Clock clock;

    @InjectMocks private SharedGroupService sharedGroupService;

    @Test
    void 공유_그룹_생성_시_초대_코드와_HOST_멤버십을_생성한다() {
        AppUser appUser = AppUser.create("apple-subject", "집집이");
        InviteCodeReservation reservation = InviteCodeReservation.create("ABC234EF");
        when(appUserRepository.findByIdAndDeletedAtIsNull(APP_USER_ID))
                .thenReturn(Optional.of(appUser));
        when(sharedGroupNameValidator.normalize(" 우리 집 ")).thenReturn("우리 집");
        when(inviteCodeGenerator.generate()).thenReturn("ABC234EF");
        when(inviteCodeReservationRepository.insertIfAbsent("ABC234EF")).thenReturn(1);
        when(inviteCodeReservationRepository.getReferenceById("ABC234EF")).thenReturn(reservation);
        when(sharedGroupRepository.save(any(SharedGroup.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(sharedGroupMembershipRepository.save(any(SharedGroupMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateSharedGroupResponse response =
                sharedGroupService.createSharedGroup(
                        APP_USER_ID, new CreateSharedGroupRequest(" 우리 집 "));

        assertThat(response.name()).isEqualTo("우리 집");
        assertThat(response.inviteCode()).isEqualTo("ABC234EF");
        assertThat(response.myRole()).isEqualTo(SharedGroupRole.HOST);
        assertThat(response.createdBy().displayName()).isEqualTo("집집이");
        verify(sharedGroupMembershipRepository).flush();
    }

    @Test
    void 초대_코드_예약이_10회_충돌하면_예외가_발생한다() {
        AppUser appUser = AppUser.create("apple-subject", "집집이");
        when(appUserRepository.findByIdAndDeletedAtIsNull(APP_USER_ID))
                .thenReturn(Optional.of(appUser));
        when(sharedGroupNameValidator.normalize("우리 집")).thenReturn("우리 집");
        when(inviteCodeGenerator.generate()).thenReturn("DUP234EF");
        when(inviteCodeReservationRepository.insertIfAbsent("DUP234EF")).thenReturn(0);

        assertThatThrownBy(
                        () ->
                                sharedGroupService.createSharedGroup(
                                        APP_USER_ID, new CreateSharedGroupRequest("우리 집")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(
                                                SharedGroupErrorCode
                                                        .INVITE_CODE_GENERATION_FAILED));
    }

    @Test
    void 상세_조회에서_활성_멤버십이_없으면_공유_그룹_없음으로_처리한다() {
        AppUser appUser = AppUser.create("apple-subject", "집집이");
        UUID sharedGroupId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(appUserRepository.findByIdAndDeletedAtIsNull(APP_USER_ID))
                .thenReturn(Optional.of(appUser));
        when(sharedGroupQueryRepository.findDetail(APP_USER_ID, sharedGroupId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sharedGroupService.findSharedGroup(APP_USER_ID, sharedGroupId))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(SharedGroupErrorCode.SHARED_GROUP_NOT_FOUND));
    }

    @Test
    void HOST는_공유_그룹_이름을_trim하여_수정할_수_있다() {
        AppUser host = AppUser.create("host-subject", "방장");
        SharedGroup sharedGroup = createSharedGroup(host);
        givenMembership(host, sharedGroup, SharedGroupRole.HOST);

        SharedGroupUpdateResponse response =
                sharedGroupService.updateName(host.getId(), sharedGroup.getId(), "  여름 여행  ");

        assertThat(sharedGroup.getName()).isEqualTo("여름 여행");
        assertThat(response.id()).isEqualTo(sharedGroup.getId());
        assertThat(response.name()).isEqualTo("여름 여행");
        verify(sharedGroupRepository).flush();
    }

    @Test
    void MEMBER는_공유_그룹_이름을_수정할_수_없다() {
        AppUser member = AppUser.create("member-subject", "멤버");
        SharedGroup sharedGroup = createSharedGroup(member);
        givenMembership(member, sharedGroup, SharedGroupRole.MEMBER);

        assertBusinessException(
                () -> sharedGroupService.updateName(member.getId(), sharedGroup.getId(), "새 이름"),
                SharedGroupErrorCode.ONLY_HOST_CAN_UPDATE_SHARED_GROUP);
    }

    @Test
    void 활성_멤버십이_없으면_공유_그룹을_찾을_수_없다() {
        UUID appUserId = UUID.randomUUID();
        UUID sharedGroupId = UUID.randomUUID();
        when(sharedGroupMembershipRepository
                        .findWithSharedGroupAndAppUserBySharedGroupIdAndAppUserId(
                                sharedGroupId, appUserId))
                .thenReturn(Optional.empty());

        assertBusinessException(
                () -> sharedGroupService.updateName(appUserId, sharedGroupId, "새 이름"),
                SharedGroupErrorCode.SHARED_GROUP_NOT_FOUND);
    }

    @Test
    void 공백이거나_100자를_초과한_공유_그룹_이름은_거부한다() {
        assertBusinessException(
                () -> sharedGroupService.updateName(UUID.randomUUID(), UUID.randomUUID(), "  "),
                SharedGroupErrorCode.INVALID_SHARED_GROUP_NAME);
        assertBusinessException(
                () ->
                        sharedGroupService.updateName(
                                UUID.randomUUID(), UUID.randomUUID(), "a".repeat(101)),
                SharedGroupErrorCode.INVALID_SHARED_GROUP_NAME);

        verify(sharedGroupMembershipRepository, never())
                .findWithSharedGroupAndAppUserBySharedGroupIdAndAppUserId(any(), any());
    }

    @Test
    void HOST는_공유_그룹과_하위_공유집_사진을_같은_시각에_soft_delete할_수_있다() {
        AppUser host = AppUser.create("host-subject", "방장");
        SharedGroup sharedGroup = createSharedGroup(host);
        SharedAlbum sharedAlbum = SharedAlbum.create(sharedGroup, host, "여행 앨범");
        Photo photo =
                Photo.create(
                        host,
                        "iPhone 15",
                        "photos/original.jpg",
                        Instant.parse("2026-07-01T00:00:00Z"),
                        100,
                        100);
        givenMembership(host, sharedGroup, SharedGroupRole.HOST);
        Instant deletedAt = Instant.parse("2026-07-10T00:00:00Z");
        when(clock.instant()).thenReturn(deletedAt);
        when(sharedAlbumRepository.findBySharedGroupIdAndDeletedAtIsNull(sharedGroup.getId()))
                .thenReturn(java.util.List.of(sharedAlbum));
        when(sharedAlbumPhotoRepository.findActivePhotosBySharedGroupId(sharedGroup.getId()))
                .thenReturn(java.util.List.of(photo));

        sharedGroupService.delete(host.getId(), sharedGroup.getId());

        assertThat(sharedGroup.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(sharedAlbum.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(photo.getDeletedAt()).isEqualTo(deletedAt);
        verify(sharedGroupRepository).flush();
    }

    @Test
    void MEMBER는_공유_그룹을_삭제할_수_없다() {
        AppUser member = AppUser.create("member-subject", "멤버");
        SharedGroup sharedGroup = createSharedGroup(member);
        givenMembership(member, sharedGroup, SharedGroupRole.MEMBER);

        assertBusinessException(
                () -> sharedGroupService.delete(member.getId(), sharedGroup.getId()),
                SharedGroupErrorCode.ONLY_HOST_CAN_DELETE_SHARED_GROUP);
    }

    @Test
    void 삭제된_공유_그룹은_접근할_수_없다() {
        AppUser host = AppUser.create("host-subject", "방장");
        SharedGroup sharedGroup = createSharedGroup(host);
        sharedGroup.delete(Instant.now());
        givenMembership(host, sharedGroup, SharedGroupRole.HOST);

        assertBusinessException(
                () -> sharedGroupService.delete(host.getId(), sharedGroup.getId()),
                SharedGroupErrorCode.SHARED_GROUP_NOT_FOUND);
    }

    private SharedGroup createSharedGroup(AppUser appUser) {
        return SharedGroup.create(appUser, "공유 그룹", InviteCodeReservation.create("INVITE1"));
    }

    private void givenMembership(AppUser appUser, SharedGroup sharedGroup, SharedGroupRole role) {
        when(sharedGroupMembershipRepository
                        .findWithSharedGroupAndAppUserBySharedGroupIdAndAppUserId(
                                sharedGroup.getId(), appUser.getId()))
                .thenReturn(Optional.of(SharedGroupMembership.create(sharedGroup, appUser, role)));
    }

    private void assertBusinessException(ThrowingCallable action, SharedGroupErrorCode errorCode) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
