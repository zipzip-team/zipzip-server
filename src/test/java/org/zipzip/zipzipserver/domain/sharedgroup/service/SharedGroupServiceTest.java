package org.zipzip.zipzipserver.domain.sharedgroup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupErrorCode;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.request.CreateSharedGroupRequest;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.CreateSharedGroupResponse;
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
    @Mock private SharedGroupCursorCodec sharedGroupCursorCodec;
    @Mock private SharedGroupNameValidator sharedGroupNameValidator;
    @Mock private InviteCodeGenerator inviteCodeGenerator;

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
}
