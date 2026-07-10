package org.zipzip.zipzipserver.domain.user.service;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zipzip.zipzipserver.domain.auth.repository.RefreshTokenRepository;
import org.zipzip.zipzipserver.domain.reaction.repository.PhotoLikeRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMembershipRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupRepository;
import org.zipzip.zipzipserver.domain.user.code.UserErrorCode;
import org.zipzip.zipzipserver.domain.user.dto.request.UpdateUserProfileRequest;
import org.zipzip.zipzipserver.domain.user.dto.response.UserProfileResponse;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.domain.user.repository.AppUserRepository;
import org.zipzip.zipzipserver.global.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private static final int MAX_DISPLAY_NAME_LENGTH = 50;

    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PhotoLikeRepository photoLikeRepository;
    private final SharedGroupRepository sharedGroupRepository;
    private final SharedGroupMembershipRepository sharedGroupMembershipRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(UUID appUserId) {
        AppUser appUser = getActiveUser(appUserId);
        return new UserProfileResponse(appUser.getDisplayName());
    }

    @Transactional
    public UserProfileResponse updateMyProfile(UUID appUserId, UpdateUserProfileRequest request) {
        AppUser appUser = getActiveUserWithLock(appUserId);
        appUser.updateDisplayName(normalizeDisplayName(request.displayName()));
        return new UserProfileResponse(appUser.getDisplayName());
    }

    @Transactional
    public void withdraw(UUID appUserId) {
        AppUser appUser = getActiveUserWithLock(appUserId);
        Instant now = Instant.now();

        refreshTokenRepository.findWithLockByAppUserId(appUserId).stream()
                .filter(refreshToken -> refreshToken.getRevokedAt() == null)
                .forEach(refreshToken -> refreshToken.revoke(now));
        photoLikeRepository.deleteByAppUserId(appUserId);
        sharedGroupMembershipRepository.deleteByAppUserIdAndRole(appUserId, SharedGroupRole.MEMBER);
        sharedGroupRepository
                .findByCreatedByAppUserIdAndDeletedAtIsNull(appUserId)
                .forEach(sharedGroup -> sharedGroup.delete(now));
        appUser.withdraw(now);
    }

    private AppUser getActiveUser(UUID appUserId) {
        return appUserRepository
                .findByIdAndDeletedAtIsNull(appUserId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    private AppUser getActiveUserWithLock(UUID appUserId) {
        return appUserRepository
                .findWithLockByIdAndDeletedAtIsNull(appUserId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            throw new BusinessException(UserErrorCode.INVALID_DISPLAY_NAME);
        }

        String normalizedDisplayName = displayName.strip();
        if (normalizedDisplayName.isBlank()
                || normalizedDisplayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new BusinessException(UserErrorCode.INVALID_DISPLAY_NAME);
        }

        return normalizedDisplayName;
    }
}
