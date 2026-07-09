package org.zipzip.zipzipserver.domain.auth.service;

import java.time.Clock;
import java.time.Instant;
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
import org.zipzip.zipzipserver.domain.auth.code.AuthSuccessCode;
import org.zipzip.zipzipserver.domain.auth.dto.request.AppleLoginRequest;
import org.zipzip.zipzipserver.domain.auth.dto.request.TokenRefreshRequest;
import org.zipzip.zipzipserver.domain.auth.dto.response.LoginResponse;
import org.zipzip.zipzipserver.domain.auth.dto.response.TokenRefreshResponse;
import org.zipzip.zipzipserver.domain.auth.entity.RefreshToken;
import org.zipzip.zipzipserver.domain.auth.jwt.JwtTokenProvider;
import org.zipzip.zipzipserver.domain.auth.repository.RefreshTokenRepository;
import org.zipzip.zipzipserver.domain.auth.token.RefreshTokenHasher;
import org.zipzip.zipzipserver.domain.user.code.UserErrorCode;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.domain.user.repository.AppUserRepository;
import org.zipzip.zipzipserver.global.code.GlobalErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.idempotency.ApiIdempotencyRecord;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final int MAX_DISPLAY_NAME_LENGTH = 50;
    private static final String AUTH_REFRESH_SCOPE_PREFIX = "AUTH_REFRESH:";
    private static final String AUTH_REFRESH_HTTP_METHOD = "POST";
    private static final String AUTH_REFRESH_API_PATH = "/api/v1/auth/refresh";

    private final AppleIdTokenVerifier appleIdTokenVerifier;
    private final AppleTokenClient appleTokenClient;
    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenHasher refreshTokenHasher;
    private final IdempotencyService idempotencyService;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public LoginResponse loginWithApple(AppleLoginRequest request) {
        AppleUserInfo requestUserInfo =
                appleIdTokenVerifier.verify(request.identityToken(), request.nonce());
        Optional<AppUser> foundAppUser =
                appUserRepository.findByAppleSubject(requestUserInfo.subject());
        AppUser appUser = foundAppUser.orElse(null);
        validateDisplayNameBeforeTokenExchange(appUser, request.displayName());

        AppleTokenResponse tokenResponse =
                appleTokenClient.requestToken(request.authorizationCode());
        AppleUserInfo tokenUserInfo =
                appleIdTokenVerifier.verify(tokenResponse.idToken(), request.nonce());
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

    @Transactional
    public TokenRefreshResult refreshTokens(
            TokenRefreshRequest request, String idempotencyKeyHeader) {
        JwtTokenProvider.RefreshTokenClaims claims =
                jwtTokenProvider.verifyRefreshToken(request.refreshToken());
        UUID idempotencyKey = parseIdempotencyKey(idempotencyKeyHeader);
        String refreshTokenHash = refreshTokenHasher.hash(request.refreshToken());
        String requestHash = idempotencyService.hashAuthRefreshRequest(refreshTokenHash);
        String scope = AUTH_REFRESH_SCOPE_PREFIX + claims.appUserId();
        IdempotencyService.IdempotencyStart<TokenRefreshResponse> idempotencyStart =
                idempotencyService.start(
                        scope,
                        idempotencyKey,
                        AUTH_REFRESH_HTTP_METHOD,
                        AUTH_REFRESH_API_PATH,
                        requestHash,
                        TokenRefreshResponse.class);

        if (idempotencyStart.replayed()) {
            return new TokenRefreshResult(idempotencyStart.replayResponse(), true);
        }

        TokenRefreshResponse response =
                rotateRefreshToken(refreshTokenHash, claims, idempotencyStart.record());
        return new TokenRefreshResult(response, false);
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
        TokenIssueResult tokenIssueResult = issueTokens(appUser, tokenFamilyId);

        return tokenIssueResult;
    }

    private TokenIssueResult issueTokens(AppUser appUser, UUID tokenFamilyId) {
        String accessToken = jwtTokenProvider.generateAccessToken(appUser.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(appUser.getId(), tokenFamilyId);
        RefreshToken refreshTokenEntity = createRefreshToken(appUser, refreshToken, tokenFamilyId);
        RefreshToken savedRefreshTokenEntity = refreshTokenRepository.save(refreshTokenEntity);

        return new TokenIssueResult(accessToken, refreshToken, savedRefreshTokenEntity);
    }

    private RefreshToken createRefreshToken(
            AppUser appUser, String refreshToken, UUID tokenFamilyId) {
        return RefreshToken.create(
                appUser,
                refreshTokenHasher.hash(refreshToken),
                tokenFamilyId,
                jwtTokenProvider.getRefreshTokenExpiresAt());
    }

    private TokenRefreshResponse rotateRefreshToken(
            String refreshTokenHash,
            JwtTokenProvider.RefreshTokenClaims claims,
            ApiIdempotencyRecord idempotencyRecord) {
        RefreshToken currentRefreshToken =
                refreshTokenRepository
                        .findWithLockByTokenHash(refreshTokenHash)
                        .orElseThrow(
                                () -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        validateRefreshTokenOwnerAndFamily(currentRefreshToken, claims);
        validateRefreshTokenAppUser(currentRefreshToken.getAppUser());
        validateRefreshTokenState(currentRefreshToken);

        Instant now = Instant.now(clock);
        TokenIssueResult tokenIssueResult =
                issueTokens(
                        currentRefreshToken.getAppUser(), currentRefreshToken.getTokenFamilyId());
        currentRefreshToken.revoke(now);
        currentRefreshToken.replaceBy(tokenIssueResult.refreshTokenEntity());

        TokenRefreshResponse response =
                new TokenRefreshResponse(
                        tokenIssueResult.accessToken(),
                        tokenIssueResult.refreshToken(),
                        TOKEN_TYPE,
                        jwtTokenProvider.getAccessTokenExpiresIn());
        idempotencyService.complete(
                idempotencyRecord, AuthSuccessCode.AUTH_TOKEN_REFRESHED, response);

        return response;
    }

    private void validateRefreshTokenOwnerAndFamily(
            RefreshToken refreshToken, JwtTokenProvider.RefreshTokenClaims claims) {
        if (!refreshToken.getAppUser().getId().equals(claims.appUserId())) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (!refreshToken.getTokenFamilyId().equals(claims.tokenFamilyId())) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    private void validateRefreshTokenAppUser(AppUser appUser) {
        if (appUser.isDeleted()) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
    }

    private void validateRefreshTokenState(RefreshToken refreshToken) {
        Instant now = Instant.now(clock);

        if (!refreshToken.getExpiresAt().isAfter(now)) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        if (refreshToken.getRevokedAt() != null
                || refreshToken.getReplacedByRefreshToken() != null) {
            revokeTokenFamily(refreshToken.getAppUser(), refreshToken.getTokenFamilyId(), now);
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        }
    }

    private void revokeTokenFamily(AppUser appUser, UUID tokenFamilyId, Instant revokedAt) {
        refreshTokenRepository
                .findWithLockByAppUserIdAndTokenFamilyId(appUser.getId(), tokenFamilyId)
                .stream()
                .filter(refreshToken -> refreshToken.getRevokedAt() == null)
                .forEach(refreshToken -> refreshToken.revoke(revokedAt));
    }

    private UUID parseIdempotencyKey(String idempotencyKeyHeader) {
        try {
            return UUID.fromString(idempotencyKeyHeader);
        } catch (Exception exception) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
    }

    private record AppUserLoginResult(AppUser appUser, boolean isNewUser, boolean isRestoredUser) {}

    private record TokenIssueResult(
            String accessToken, String refreshToken, RefreshToken refreshTokenEntity) {}

    public record TokenRefreshResult(TokenRefreshResponse response, boolean replayed) {}
}
