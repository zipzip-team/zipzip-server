package org.zipzip.zipzipserver.domain.auth.service;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zipzip.zipzipserver.domain.auth.apple.AppleIdTokenVerifier;
import org.zipzip.zipzipserver.domain.auth.apple.AppleTokenClient;
import org.zipzip.zipzipserver.domain.auth.apple.AppleTokenResponse;
import org.zipzip.zipzipserver.domain.auth.apple.AppleUserInfo;
import org.zipzip.zipzipserver.domain.auth.code.AuthErrorCode;
import org.zipzip.zipzipserver.domain.auth.dto.request.AppleLoginRequest;
import org.zipzip.zipzipserver.domain.auth.dto.response.LoginResponse;
import org.zipzip.zipzipserver.domain.auth.entity.RefreshToken;
import org.zipzip.zipzipserver.domain.auth.jwt.JwtTokenProvider;
import org.zipzip.zipzipserver.domain.auth.repository.RefreshTokenRepository;
import org.zipzip.zipzipserver.domain.auth.token.RefreshTokenHasher;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.domain.user.repository.AppUserRepository;
import org.zipzip.zipzipserver.global.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final int MAX_DISPLAY_NAME_LENGTH = 50;

    private final AppleIdTokenVerifier appleIdTokenVerifier;
    private final AppleTokenClient appleTokenClient;
    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenHasher refreshTokenHasher;

    @Transactional
    public LoginResponse loginWithApple(AppleLoginRequest request) {
        AppleUserInfo requestUserInfo = appleIdTokenVerifier.verify(request.identityToken());
        Optional<AppUser> foundAppUser =
                appUserRepository.findByAppleSubject(requestUserInfo.subject());
        AppUser appUser = foundAppUser.orElse(null);
        validateDisplayNameBeforeTokenExchange(appUser, request.displayName());

        AppleTokenResponse tokenResponse =
                appleTokenClient.requestToken(request.authorizationCode());
        AppleUserInfo tokenUserInfo = appleIdTokenVerifier.verify(tokenResponse.idToken());
        validateSameAppleUser(requestUserInfo, tokenUserInfo);

        AppUserLoginResult loginResult =
                findOrCreateAppUser(appUser, requestUserInfo.subject(), request.displayName());
        TokenIssueResult tokenIssueResult = issueTokens(loginResult.appUser());

        return new LoginResponse(
                tokenIssueResult.accessToken(),
                tokenIssueResult.refreshToken(),
                TOKEN_TYPE,
                jwtTokenProvider.getAccessTokenExpiresIn(),
                loginResult.isNewUser(),
                loginResult.isRestoredUser(),
                new LoginResponse.UserSummary(
                        loginResult.appUser().getId(), loginResult.appUser().getDisplayName()));
    }

    private void validateSameAppleUser(AppleUserInfo requestUserInfo, AppleUserInfo tokenUserInfo) {
        if (!requestUserInfo.subject().equals(tokenUserInfo.subject())) {
            throw new BusinessException(AuthErrorCode.INVALID_APPLE_AUTHORIZATION_CODE);
        }
    }

    private void validateDisplayNameBeforeTokenExchange(AppUser appUser, String displayName) {
        if (appUser == null || appUser.isDeleted()) {
            normalizeRequiredDisplayName(displayName);
        }
    }

    private AppUserLoginResult findOrCreateAppUser(
            AppUser foundAppUser, String appleSubject, String displayName) {
        if (foundAppUser != null) {
            return restoreIfDeleted(foundAppUser, displayName);
        }

        return createAppUser(appleSubject, displayName);
    }

    private AppUserLoginResult restoreIfDeleted(AppUser appUser, String displayName) {
        if (!appUser.isDeleted()) {
            return new AppUserLoginResult(appUser, false, false);
        }

        appUser.restore(normalizeRequiredDisplayName(displayName));
        return new AppUserLoginResult(appUser, false, true);
    }

    private AppUserLoginResult createAppUser(String appleSubject, String displayName) {
        String normalizedDisplayName = normalizeRequiredDisplayName(displayName);
        int inserted =
                appUserRepository.insertIfAppleSubjectAbsent(
                        UUID.randomUUID(), appleSubject, normalizedDisplayName);
        AppUser appUser =
                appUserRepository
                        .findByAppleSubject(appleSubject)
                        .orElseThrow(() -> new IllegalStateException("Apple 사용자 생성 후 조회에 실패했습니다."));

        if (inserted == 0) {
            return restoreIfDeleted(appUser, displayName);
        }

        return new AppUserLoginResult(appUser, true, false);
    }

    private String normalizeRequiredDisplayName(String displayName) {
        if (displayName == null) {
            throw new BusinessException(AuthErrorCode.DISPLAY_NAME_REQUIRED);
        }

        String normalizedDisplayName = displayName.strip();
        if (normalizedDisplayName.isBlank()) {
            throw new BusinessException(AuthErrorCode.DISPLAY_NAME_REQUIRED);
        }

        if (normalizedDisplayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new BusinessException(AuthErrorCode.INVALID_DISPLAY_NAME);
        }

        return normalizedDisplayName;
    }

    private TokenIssueResult issueTokens(AppUser appUser) {
        UUID tokenFamilyId = UUID.randomUUID();
        String accessToken = jwtTokenProvider.generateAccessToken(appUser.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(appUser.getId(), tokenFamilyId);
        RefreshToken refreshTokenEntity =
                RefreshToken.create(
                        appUser,
                        refreshTokenHasher.hash(refreshToken),
                        tokenFamilyId,
                        jwtTokenProvider.getRefreshTokenExpiresAt());
        refreshTokenRepository.save(refreshTokenEntity);

        return new TokenIssueResult(accessToken, refreshToken);
    }

    private record AppUserLoginResult(AppUser appUser, boolean isNewUser, boolean isRestoredUser) {}

    private record TokenIssueResult(String accessToken, String refreshToken) {}
}
