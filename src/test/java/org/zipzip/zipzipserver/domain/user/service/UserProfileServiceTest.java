package org.zipzip.zipzipserver.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zipzip.zipzipserver.domain.auth.entity.RefreshToken;
import org.zipzip.zipzipserver.domain.auth.repository.RefreshTokenRepository;
import org.zipzip.zipzipserver.domain.reaction.repository.PhotoLikeRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.InviteCodeReservation;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMembershipRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupRepository;
import org.zipzip.zipzipserver.domain.user.code.UserErrorCode;
import org.zipzip.zipzipserver.domain.user.dto.request.UpdateUserProfileRequest;
import org.zipzip.zipzipserver.domain.user.dto.response.UserProfileResponse;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.domain.user.repository.AppUserRepository;
import org.zipzip.zipzipserver.global.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PhotoLikeRepository photoLikeRepository;
    @Mock private SharedGroupRepository sharedGroupRepository;
    @Mock private SharedGroupMembershipRepository sharedGroupMembershipRepository;

    @InjectMocks private UserProfileService userProfileService;

    @Test
    void 내_프로필은_사용자_이름만_반환한다() {
        AppUser appUser = AppUser.create("apple-subject", "집집이");
        when(appUserRepository.findByIdAndDeletedAtIsNull(appUser.getId()))
                .thenReturn(Optional.of(appUser));

        UserProfileResponse response = userProfileService.getMyProfile(appUser.getId());

        assertThat(response).isEqualTo(new UserProfileResponse("집집이"));
    }

    @Test
    void 내_프로필을_수정할_때_이름의_앞뒤_공백을_제거한다() {
        AppUser appUser = AppUser.create("apple-subject", "집집이");
        when(appUserRepository.findWithLockByIdAndDeletedAtIsNull(appUser.getId()))
                .thenReturn(Optional.of(appUser));

        UserProfileResponse response =
                userProfileService.updateMyProfile(
                        appUser.getId(), new UpdateUserProfileRequest(" 새 집집이 "));

        assertThat(response).isEqualTo(new UserProfileResponse("새 집집이"));
        assertThat(appUser.getDisplayName()).isEqualTo("새 집집이");
    }

    @Test
    void 공백_이름으로_내_프로필을_수정하면_예외가_발생한다() {
        AppUser appUser = AppUser.create("apple-subject", "집집이");
        when(appUserRepository.findWithLockByIdAndDeletedAtIsNull(appUser.getId()))
                .thenReturn(Optional.of(appUser));

        assertThatThrownBy(
                        () ->
                                userProfileService.updateMyProfile(
                                        appUser.getId(), new UpdateUserProfileRequest(" ")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(UserErrorCode.INVALID_DISPLAY_NAME));
    }

    @Test
    void 사용자_탈퇴시_관련_세션과_참여_정보를_정리한다() {
        AppUser appUser = AppUser.create("apple-subject", "집집이");
        RefreshToken refreshToken =
                RefreshToken.create(
                        appUser,
                        "token-hash",
                        UUID.randomUUID(),
                        Instant.parse("2999-01-01T00:00:00Z"));
        SharedGroup sharedGroup =
                SharedGroup.create(appUser, "우리 집", InviteCodeReservation.create("invite-code"));
        when(appUserRepository.findWithLockByIdAndDeletedAtIsNull(appUser.getId()))
                .thenReturn(Optional.of(appUser));
        when(refreshTokenRepository.findWithLockByAppUserId(appUser.getId()))
                .thenReturn(List.of(refreshToken));
        when(sharedGroupRepository.findByCreatedByAppUserIdAndDeletedAtIsNull(appUser.getId()))
                .thenReturn(List.of(sharedGroup));

        userProfileService.withdraw(appUser.getId());

        assertThat(appUser.isDeleted()).isTrue();
        assertThat(appUser.getDisplayName()).isEqualTo(AppUser.WITHDRAWN_DISPLAY_NAME);
        assertThat(refreshToken.getRevokedAt()).isNotNull();
        assertThat(sharedGroup.getDeletedAt()).isNotNull();
        verify(photoLikeRepository).deleteByAppUserId(appUser.getId());
        verify(sharedGroupMembershipRepository)
                .deleteByAppUserIdAndRole(appUser.getId(), SharedGroupRole.MEMBER);
    }
}
