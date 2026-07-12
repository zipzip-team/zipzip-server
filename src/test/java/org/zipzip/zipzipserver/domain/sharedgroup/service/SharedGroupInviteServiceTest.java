package org.zipzip.zipzipserver.domain.sharedgroup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumPhotoRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupErrorCode;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.request.SharedGroupJoinRequest;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.InviteCodeResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupJoinPreviewResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupJoinResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.InviteCodeReservation;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupMembership;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMemberRow;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMembershipRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupRepository;
import org.zipzip.zipzipserver.domain.storage.ObjectStorageService;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.domain.user.repository.AppUserRepository;
import org.zipzip.zipzipserver.global.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class SharedGroupInviteServiceTest {

    private static final String INVITE_CODE = "ZZ7K9P2Q";

    @Mock private SharedGroupRepository sharedGroupRepository;
    @Mock private SharedGroupMembershipRepository sharedGroupMembershipRepository;
    @Mock private SharedAlbumPhotoRepository sharedAlbumPhotoRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private ObjectStorageService objectStorageService;

    @InjectMocks private SharedGroupInviteService sharedGroupInviteService;

    @Test
    void 방장과_멤버는_초대_코드를_조회할_수_있다() {
        AppUser host = AppUser.create("host", "방장");
        AppUser member = AppUser.create("member", "멤버");
        SharedGroup sharedGroup = sharedGroup(host);
        when(sharedGroupRepository.findActiveWithInviteCodeById(sharedGroup.getId()))
                .thenReturn(Optional.of(sharedGroup));
        when(sharedGroupMembershipRepository.findActiveBySharedGroupIdAndAppUserId(
                        sharedGroup.getId(), host.getId()))
                .thenReturn(
                        Optional.of(
                                SharedGroupMembership.create(
                                        sharedGroup, host, SharedGroupRole.HOST)));
        when(sharedGroupMembershipRepository.findActiveBySharedGroupIdAndAppUserId(
                        sharedGroup.getId(), member.getId()))
                .thenReturn(
                        Optional.of(
                                SharedGroupMembership.create(
                                        sharedGroup, member, SharedGroupRole.MEMBER)));

        InviteCodeResponse hostResponse =
                sharedGroupInviteService.findInviteCode(sharedGroup.getId(), host.getId());
        InviteCodeResponse memberResponse =
                sharedGroupInviteService.findInviteCode(sharedGroup.getId(), member.getId());

        assertThat(hostResponse.inviteCode()).isEqualTo(INVITE_CODE);
        assertThat(memberResponse.inviteCode()).isEqualTo(INVITE_CODE);
    }

    @Test
    void 초대_코드_조회는_활성_멤버십이_없으면_공유_그룹_없음으로_응답한다() {
        AppUser host = AppUser.create("host", "방장");
        AppUser stranger = AppUser.create("stranger", "외부 사용자");
        SharedGroup sharedGroup = sharedGroup(host);
        when(sharedGroupRepository.findActiveWithInviteCodeById(sharedGroup.getId()))
                .thenReturn(Optional.of(sharedGroup));
        when(sharedGroupMembershipRepository.findActiveBySharedGroupIdAndAppUserId(
                        sharedGroup.getId(), stranger.getId()))
                .thenReturn(Optional.empty());

        assertBusinessException(
                () ->
                        sharedGroupInviteService.findInviteCode(
                                sharedGroup.getId(), stranger.getId()),
                SharedGroupErrorCode.SHARED_GROUP_NOT_FOUND);
    }

    @Test
    void 초대_코드로_활성_공유_그룹의_참여_미리보기를_조회한다() {
        AppUser host = AppUser.create("host", "방장");
        AppUser applicant = AppUser.create("applicant", "참여 예정자");
        AppUser member = AppUser.create("member", "멤버");
        SharedGroup sharedGroup = sharedGroup(host);
        when(appUserRepository.findById(applicant.getId())).thenReturn(Optional.of(applicant));
        when(sharedGroupRepository.findActiveWithCreatorByInviteCode(INVITE_CODE))
                .thenReturn(Optional.of(sharedGroup));
        when(sharedGroupMembershipRepository.findActiveMembers(any(), any()))
                .thenReturn(
                        List.of(
                                new SharedGroupMemberRow(
                                        java.util.UUID.randomUUID(),
                                        host.getId(),
                                        host.getDisplayName(),
                                        SharedGroupRole.HOST,
                                        Instant.parse("2026-07-10T00:00:00Z")),
                                new SharedGroupMemberRow(
                                        java.util.UUID.randomUUID(),
                                        member.getId(),
                                        member.getDisplayName(),
                                        SharedGroupRole.MEMBER,
                                        Instant.parse("2026-07-10T00:01:00Z"))));
        when(sharedGroupMembershipRepository.countActiveMembers(sharedGroup.getId()))
                .thenReturn(2L);
        when(sharedGroupMembershipRepository.existsActiveBySharedGroupIdAndAppUserId(
                        sharedGroup.getId(), applicant.getId()))
                .thenReturn(false);
        when(sharedAlbumPhotoRepository.findRepresentativePhotoBySharedGroupId(any(), any()))
                .thenReturn(List.of());

        SharedGroupJoinPreviewResponse response =
                sharedGroupInviteService.previewJoin(applicant.getId(), " " + INVITE_CODE + " ");

        assertThat(response.sharedGroupId()).isEqualTo(sharedGroup.getId());
        assertThat(response.name()).isEqualTo("우리 집");
        assertThat(response.createdBy().displayName()).isEqualTo("방장");
        assertThat(response.memberCount()).isEqualTo(2);
        assertThat(response.members())
                .extracting(SharedGroupJoinPreviewResponse.Member::displayName)
                .containsExactly("방장", "멤버");
        assertThat(response.representativeImageUrl()).isNull();
        assertThat(response.alreadyJoined()).isFalse();
    }

    @Test
    void 이미_참여한_사용자는_오류_대신_참여_완료_상태를_포함한_미리보기를_받는다() {
        AppUser host = AppUser.create("host", "방장");
        SharedGroup sharedGroup = sharedGroup(host);
        when(appUserRepository.findById(host.getId())).thenReturn(Optional.of(host));
        when(sharedGroupRepository.findActiveWithCreatorByInviteCode(INVITE_CODE))
                .thenReturn(Optional.of(sharedGroup));
        when(sharedGroupMembershipRepository.findActiveMembers(any(), any())).thenReturn(List.of());
        when(sharedGroupMembershipRepository.countActiveMembers(sharedGroup.getId()))
                .thenReturn(1L);
        when(sharedGroupMembershipRepository.existsActiveBySharedGroupIdAndAppUserId(
                        sharedGroup.getId(), host.getId()))
                .thenReturn(true);
        when(sharedAlbumPhotoRepository.findRepresentativePhotoBySharedGroupId(any(), any()))
                .thenReturn(List.of());

        SharedGroupJoinPreviewResponse response =
                sharedGroupInviteService.previewJoin(host.getId(), INVITE_CODE);

        assertThat(response.alreadyJoined()).isTrue();
    }

    @Test
    void 존재하지_않거나_삭제된_공유_그룹의_초대_코드로_미리보기를_조회할_수_없다() {
        AppUser applicant = AppUser.create("applicant", "참여 예정자");
        when(appUserRepository.findById(applicant.getId())).thenReturn(Optional.of(applicant));
        when(sharedGroupRepository.findActiveWithCreatorByInviteCode(INVITE_CODE))
                .thenReturn(Optional.empty());

        assertBusinessException(
                () -> sharedGroupInviteService.previewJoin(applicant.getId(), INVITE_CODE),
                SharedGroupErrorCode.INVALID_INVITE_CODE);

        verify(sharedGroupMembershipRepository, never()).findActiveMembers(any(), any());
    }

    @Test
    void 초대_코드로_참여하면_MEMBER_멤버십을_생성한다() {
        AppUser host = AppUser.create("host", "방장");
        AppUser newMember = AppUser.create("new-member", "새 멤버");
        SharedGroup sharedGroup = sharedGroup(host);
        when(sharedGroupRepository.findActiveByInviteCode(INVITE_CODE))
                .thenReturn(Optional.of(sharedGroup));
        when(sharedGroupMembershipRepository.existsActiveBySharedGroupIdAndAppUserId(
                        sharedGroup.getId(), newMember.getId()))
                .thenReturn(false);
        when(appUserRepository.findById(newMember.getId())).thenReturn(Optional.of(newMember));
        when(sharedGroupMembershipRepository.saveAndFlush(any(SharedGroupMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SharedGroupJoinResponse response =
                sharedGroupInviteService.join(
                        newMember.getId(), new SharedGroupJoinRequest(" " + INVITE_CODE + " "));

        ArgumentCaptor<SharedGroupMembership> membershipCaptor =
                ArgumentCaptor.forClass(SharedGroupMembership.class);
        verify(sharedGroupMembershipRepository).saveAndFlush(membershipCaptor.capture());
        assertThat(membershipCaptor.getValue().getRole()).isEqualTo(SharedGroupRole.MEMBER);
        assertThat(response.sharedGroupId()).isEqualTo(sharedGroup.getId());
        assertThat(response.name()).isEqualTo("우리 집");
        assertThat(response.myRole()).isEqualTo(SharedGroupRole.MEMBER);
        assertThat(response.joinedAt()).isNotNull();
    }

    @Test
    void 존재하지_않거나_삭제된_공유_그룹의_초대_코드는_거부한다() {
        AppUser appUser = AppUser.create("member", "멤버");
        when(sharedGroupRepository.findActiveByInviteCode(INVITE_CODE))
                .thenReturn(Optional.empty());

        assertBusinessException(
                () ->
                        sharedGroupInviteService.join(
                                appUser.getId(), new SharedGroupJoinRequest(INVITE_CODE)),
                SharedGroupErrorCode.INVALID_INVITE_CODE);

        verify(sharedGroupMembershipRepository, never()).saveAndFlush(any());
    }

    @Test
    void 이미_활성_멤버십이_있으면_참여를_거부한다() {
        AppUser host = AppUser.create("host", "방장");
        AppUser member = AppUser.create("member", "멤버");
        SharedGroup sharedGroup = sharedGroup(host);
        when(sharedGroupRepository.findActiveByInviteCode(INVITE_CODE))
                .thenReturn(Optional.of(sharedGroup));
        when(sharedGroupMembershipRepository.existsActiveBySharedGroupIdAndAppUserId(
                        sharedGroup.getId(), member.getId()))
                .thenReturn(true);

        assertBusinessException(
                () ->
                        sharedGroupInviteService.join(
                                member.getId(), new SharedGroupJoinRequest(INVITE_CODE)),
                SharedGroupErrorCode.ALREADY_JOINED_SHARED_GROUP);

        verify(sharedGroupMembershipRepository, never()).saveAndFlush(any());
    }

    @Test
    void 나간_사용자는_멤버십이_없으면_재참여할_수_있다() {
        AppUser host = AppUser.create("host", "방장");
        AppUser leftMember = AppUser.create("left-member", "나간 멤버");
        SharedGroup sharedGroup = sharedGroup(host);
        when(sharedGroupRepository.findActiveByInviteCode(INVITE_CODE))
                .thenReturn(Optional.of(sharedGroup));
        when(sharedGroupMembershipRepository.existsActiveBySharedGroupIdAndAppUserId(
                        sharedGroup.getId(), leftMember.getId()))
                .thenReturn(false);
        when(appUserRepository.findById(leftMember.getId())).thenReturn(Optional.of(leftMember));
        when(sharedGroupMembershipRepository.saveAndFlush(any(SharedGroupMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SharedGroupJoinResponse response =
                sharedGroupInviteService.join(
                        leftMember.getId(), new SharedGroupJoinRequest(INVITE_CODE));

        assertThat(response.myRole()).isEqualTo(SharedGroupRole.MEMBER);
        verify(sharedGroupMembershipRepository).saveAndFlush(any(SharedGroupMembership.class));
    }

    @Test
    void 활성_MEMBER는_공유_그룹에서_나가면_멤버십을_물리_삭제한다() {
        AppUser host = AppUser.create("host", "방장");
        AppUser member = AppUser.create("member", "멤버");
        SharedGroup sharedGroup = sharedGroup(host);
        SharedGroupMembership membership =
                SharedGroupMembership.create(sharedGroup, member, SharedGroupRole.MEMBER);
        when(sharedGroupMembershipRepository.findActiveBySharedGroupIdAndAppUserId(
                        sharedGroup.getId(), member.getId()))
                .thenReturn(Optional.of(membership));

        sharedGroupInviteService.leave(sharedGroup.getId(), member.getId());

        verify(sharedGroupMembershipRepository).delete(membership);
    }

    @Test
    void 방장은_공유_그룹에서_나갈_수_없다() {
        AppUser host = AppUser.create("host", "방장");
        SharedGroup sharedGroup = sharedGroup(host);
        SharedGroupMembership membership =
                SharedGroupMembership.create(sharedGroup, host, SharedGroupRole.HOST);
        when(sharedGroupMembershipRepository.findActiveBySharedGroupIdAndAppUserId(
                        sharedGroup.getId(), host.getId()))
                .thenReturn(Optional.of(membership));

        assertBusinessException(
                () -> sharedGroupInviteService.leave(sharedGroup.getId(), host.getId()),
                SharedGroupErrorCode.HOST_CANNOT_LEAVE_SHARED_GROUP);

        verify(sharedGroupMembershipRepository, never()).delete(any());
    }

    private SharedGroup sharedGroup(AppUser host) {
        return SharedGroup.create(host, "우리 집", InviteCodeReservation.create(INVITE_CODE));
    }

    private void assertBusinessException(Runnable runnable, SharedGroupErrorCode errorCode) {
        assertThatThrownBy(runnable::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
