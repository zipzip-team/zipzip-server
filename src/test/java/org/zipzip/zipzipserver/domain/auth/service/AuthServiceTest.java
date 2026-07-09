package org.zipzip.zipzipserver.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zipzip.zipzipserver.domain.auth.apple.AppleIdTokenVerifier;
import org.zipzip.zipzipserver.domain.auth.apple.AppleTokenClient;
import org.zipzip.zipzipserver.domain.auth.apple.AppleTokenResponse;
import org.zipzip.zipzipserver.domain.auth.apple.AppleUserInfo;
import org.zipzip.zipzipserver.domain.auth.code.AuthErrorCode;
import org.zipzip.zipzipserver.domain.auth.dto.request.AppleLoginRequest;
import org.zipzip.zipzipserver.domain.auth.dto.request.LogoutRequest;
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
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.idempotency.ApiIdempotencyRecord;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String IDENTITY_TOKEN = "identity-token";
    private static final String AUTHORIZATION_CODE = "authorization-code";
    private static final String NONCE = "nonce";
    private static final String TOKEN_RESPONSE_ID_TOKEN = "token-response-id-token";
    private static final String APPLE_SUBJECT = "apple-subject";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String REFRESH_TOKEN_HASH = "refresh-token-hash";
    private static final String ROTATED_ACCESS_TOKEN = "rotated-access-token";
    private static final String ROTATED_REFRESH_TOKEN = "rotated-refresh-token";
    private static final String ROTATED_REFRESH_TOKEN_HASH = "rotated-refresh-token-hash";
    private static final String IDEMPOTENCY_KEY = "54cf8d7e-a23e-4e76-90f7-603f122b1507";
    private static final String IDEMPOTENCY_REQUEST_HASH = "idempotency-request-hash";

    @Mock private AppleIdTokenVerifier appleIdTokenVerifier;
    @Mock private AppleTokenClient appleTokenClient;
    @Mock private AppUserRepository appUserRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenHasher refreshTokenHasher;
    @Mock private RefreshTokenValidator refreshTokenValidator;
    @Mock private IdempotencyService idempotencyService;

    @InjectMocks private AuthService authService;

    @Test
    void 신규_사용자를_생성하고_토큰을_발급한다() {
        AppUser insertedAppUser = AppUser.create(APPLE_SUBJECT, "집집이");
        givenAppleVerification(APPLE_SUBJECT);
        givenTokenIssue();
        when(appUserRepository.findByAppleSubject(APPLE_SUBJECT))
                .thenReturn(Optional.empty(), Optional.of(insertedAppUser));
        when(appUserRepository.insertIfAppleSubjectAbsent(
                        any(UUID.class), eq(APPLE_SUBJECT), eq("집집이")))
                .thenReturn(1);

        LoginResponse response =
                authService.loginWithApple(
                        new AppleLoginRequest(IDENTITY_TOKEN, AUTHORIZATION_CODE, NONCE, " 집집이 "));

        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800L);
        assertThat(response.isNewUser()).isTrue();
        assertThat(response.isRestoredUser()).isFalse();
        assertThat(response.user().displayName()).isEqualTo("집집이");
        verify(appUserRepository)
                .insertIfAppleSubjectAbsent(any(UUID.class), eq(APPLE_SUBJECT), eq("집집이"));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void 첫_로그인_동시_요청에서_이미_생성된_사용자를_다시_조회해_로그인한다() {
        AppUser appUser = AppUser.create(APPLE_SUBJECT, "집집이");
        givenAppleVerification(APPLE_SUBJECT);
        givenTokenIssue();
        when(appUserRepository.findByAppleSubject(APPLE_SUBJECT))
                .thenReturn(Optional.empty(), Optional.of(appUser));
        when(appUserRepository.insertIfAppleSubjectAbsent(
                        any(UUID.class), eq(APPLE_SUBJECT), eq("집집이")))
                .thenReturn(0);

        LoginResponse response =
                authService.loginWithApple(
                        new AppleLoginRequest(IDENTITY_TOKEN, AUTHORIZATION_CODE, NONCE, " 집집이 "));

        assertThat(response.isNewUser()).isFalse();
        assertThat(response.isRestoredUser()).isFalse();
        assertThat(response.user().id()).isEqualTo(appUser.getId());
        assertThat(response.user().displayName()).isEqualTo("집집이");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void 기존_활성_사용자는_표시_이름_없이도_로그인한다() {
        AppUser appUser = AppUser.create(APPLE_SUBJECT, "기존 사용자");
        givenAppleVerification(APPLE_SUBJECT);
        givenTokenIssue();
        when(appUserRepository.findByAppleSubject(APPLE_SUBJECT)).thenReturn(Optional.of(appUser));

        LoginResponse response =
                authService.loginWithApple(
                        new AppleLoginRequest(IDENTITY_TOKEN, AUTHORIZATION_CODE, NONCE, null));

        assertThat(response.isNewUser()).isFalse();
        assertThat(response.isRestoredUser()).isFalse();
        assertThat(response.user().id()).isEqualTo(appUser.getId());
        assertThat(response.user().displayName()).isEqualTo("기존 사용자");
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    @Test
    void 탈퇴_사용자는_표시_이름을_받아_복구한다() {
        AppUser appUser = AppUser.create(APPLE_SUBJECT, "기존 사용자");
        appUser.withdraw(Instant.parse("2026-07-08T00:00:00Z"));
        givenAppleVerification(APPLE_SUBJECT);
        givenTokenIssue();
        when(appUserRepository.findByAppleSubject(APPLE_SUBJECT)).thenReturn(Optional.of(appUser));

        LoginResponse response =
                authService.loginWithApple(
                        new AppleLoginRequest(
                                IDENTITY_TOKEN, AUTHORIZATION_CODE, NONCE, " 복구 사용자 "));

        assertThat(response.isNewUser()).isFalse();
        assertThat(response.isRestoredUser()).isTrue();
        assertThat(response.user().displayName()).isEqualTo("복구 사용자");
        assertThat(appUser.isDeleted()).isFalse();
    }

    @Test
    void 신규_사용자_표시_이름이_없으면_예외가_발생한다() {
        when(appleIdTokenVerifier.verify(IDENTITY_TOKEN, NONCE))
                .thenReturn(new AppleUserInfo(APPLE_SUBJECT, null));
        when(appUserRepository.findByAppleSubject(APPLE_SUBJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                authService.loginWithApple(
                                        new AppleLoginRequest(
                                                IDENTITY_TOKEN, AUTHORIZATION_CODE, NONCE, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(AuthErrorCode.DISPLAY_NAME_REQUIRED));

        verify(appleTokenClient, never()).requestToken(any());
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void 탈퇴_사용자_표시_이름이_없으면_authorizationCode를_소비하지_않고_예외가_발생한다() {
        AppUser appUser = AppUser.create(APPLE_SUBJECT, "기존 사용자");
        appUser.withdraw(Instant.parse("2026-07-08T00:00:00Z"));
        when(appleIdTokenVerifier.verify(IDENTITY_TOKEN, NONCE))
                .thenReturn(new AppleUserInfo(APPLE_SUBJECT, null));
        when(appUserRepository.findByAppleSubject(APPLE_SUBJECT)).thenReturn(Optional.of(appUser));

        assertThatThrownBy(
                        () ->
                                authService.loginWithApple(
                                        new AppleLoginRequest(
                                                IDENTITY_TOKEN, AUTHORIZATION_CODE, NONCE, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(AuthErrorCode.DISPLAY_NAME_REQUIRED));

        verify(appleTokenClient, never()).requestToken(any());
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void 요청_identityToken과_authorizationCode의_Apple_사용자가_다르면_예외가_발생한다() {
        when(appleIdTokenVerifier.verify(IDENTITY_TOKEN, NONCE))
                .thenReturn(new AppleUserInfo("request-subject", null));
        when(appUserRepository.findByAppleSubject("request-subject")).thenReturn(Optional.empty());
        when(appleTokenClient.requestToken(AUTHORIZATION_CODE)).thenReturn(tokenResponse());
        when(appleIdTokenVerifier.verify(TOKEN_RESPONSE_ID_TOKEN, NONCE))
                .thenReturn(new AppleUserInfo("token-subject", null));

        assertThatThrownBy(
                        () ->
                                authService.loginWithApple(
                                        new AppleLoginRequest(
                                                IDENTITY_TOKEN, AUTHORIZATION_CODE, NONCE, "집집이")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(AuthErrorCode.INVALID_APPLE_AUTHORIZATION_CODE));

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void Refresh_Token은_해시로_저장한다() {
        givenAppleVerification(APPLE_SUBJECT);
        givenTokenIssue();
        AppUser insertedAppUser = AppUser.create(APPLE_SUBJECT, "집집이");
        when(appUserRepository.findByAppleSubject(APPLE_SUBJECT))
                .thenReturn(Optional.empty(), Optional.of(insertedAppUser));
        when(appUserRepository.insertIfAppleSubjectAbsent(
                        any(UUID.class), eq(APPLE_SUBJECT), eq("집집이")))
                .thenReturn(1);
        ArgumentCaptor<RefreshToken> refreshTokenCaptor =
                ArgumentCaptor.forClass(RefreshToken.class);

        authService.loginWithApple(
                new AppleLoginRequest(IDENTITY_TOKEN, AUTHORIZATION_CODE, NONCE, "집집이"));

        verify(refreshTokenHasher).hash(REFRESH_TOKEN);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        assertThat(refreshTokenCaptor.getValue().getTokenHash()).isEqualTo(REFRESH_TOKEN_HASH);
        assertThat(refreshTokenCaptor.getValue().getTokenFamilyId()).isNotNull();
    }

    @Test
    void 유효한_Refresh_Token이면_기존_토큰을_폐기하고_같은_family의_새_토큰을_발급한다() {
        AppUser appUser = AppUser.create(APPLE_SUBJECT, "사용자");
        UUID tokenFamilyId = UUID.randomUUID();
        RefreshToken currentRefreshToken =
                RefreshToken.create(
                        appUser,
                        REFRESH_TOKEN_HASH,
                        tokenFamilyId,
                        Instant.parse("2999-01-01T00:00:00Z"));
        ApiIdempotencyRecord idempotencyRecord = processingIdempotencyRecord(appUser);
        givenRefreshTokenVerification(appUser, tokenFamilyId, idempotencyRecord);
        givenRefreshTokenRotation(currentRefreshToken);
        ArgumentCaptor<RefreshToken> savedRefreshTokenCaptor =
                ArgumentCaptor.forClass(RefreshToken.class);

        AuthService.TokenRefreshResult result =
                authService.refreshTokens(new TokenRefreshRequest(REFRESH_TOKEN), IDEMPOTENCY_KEY);

        assertThat(result.replayed()).isFalse();
        assertThat(result.response().accessToken()).isEqualTo(ROTATED_ACCESS_TOKEN);
        assertThat(result.response().refreshToken()).isEqualTo(ROTATED_REFRESH_TOKEN);
        assertThat(result.response().tokenType()).isEqualTo("Bearer");
        assertThat(result.response().expiresIn()).isEqualTo(1800L);
        assertThat(currentRefreshToken.getRevokedAt()).isNotNull();
        assertThat(currentRefreshToken.getReplacedByRefreshToken()).isNotNull();
        verify(refreshTokenRepository).save(savedRefreshTokenCaptor.capture());
        assertThat(savedRefreshTokenCaptor.getValue().getTokenFamilyId()).isEqualTo(tokenFamilyId);
        assertThat(savedRefreshTokenCaptor.getValue().getTokenHash())
                .isEqualTo(ROTATED_REFRESH_TOKEN_HASH);
        verify(idempotencyService)
                .complete(
                        eq(idempotencyRecord),
                        eq(
                                org.zipzip.zipzipserver.domain.auth.code.AuthSuccessCode
                                        .AUTH_TOKEN_REFRESHED),
                        any(TokenRefreshResponse.class));
    }

    @Test
    void 같은_멱등성_key와_같은_body의_완료_응답은_토큰을_다시_회전하지_않고_재전송한다() {
        AppUser appUser = AppUser.create(APPLE_SUBJECT, "사용자");
        UUID tokenFamilyId = UUID.randomUUID();
        TokenRefreshResponse replayResponse =
                new TokenRefreshResponse(
                        ROTATED_ACCESS_TOKEN, ROTATED_REFRESH_TOKEN, "Bearer", 1800L);
        givenRefreshTokenClaimsAndHash(appUser, tokenFamilyId);
        when(idempotencyService.start(
                        any(),
                        any(UUID.class),
                        any(),
                        any(),
                        eq(IDEMPOTENCY_REQUEST_HASH),
                        eq(TokenRefreshResponse.class)))
                .thenReturn(new IdempotencyService.IdempotencyStart<>(null, replayResponse, true));

        AuthService.TokenRefreshResult result =
                authService.refreshTokens(new TokenRefreshRequest(REFRESH_TOKEN), IDEMPOTENCY_KEY);

        assertThat(result.replayed()).isTrue();
        assertThat(result.response()).isEqualTo(replayResponse);
        verify(refreshTokenRepository, never()).findWithLockByTokenHash(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void DB_만료시각이_지난_Refresh_Token은_만료_오류로_거부한다() {
        AppUser appUser = AppUser.create(APPLE_SUBJECT, "사용자");
        UUID tokenFamilyId = UUID.randomUUID();
        RefreshToken expiredRefreshToken =
                RefreshToken.create(
                        appUser,
                        REFRESH_TOKEN_HASH,
                        tokenFamilyId,
                        Instant.parse("2000-01-01T00:00:00Z"));
        givenRefreshTokenVerification(appUser, tokenFamilyId, processingIdempotencyRecord(appUser));
        when(refreshTokenRepository.findWithLockByTokenHash(REFRESH_TOKEN_HASH))
                .thenReturn(Optional.of(expiredRefreshToken));

        assertRefreshTokenError(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
    }

    @Test
    void 탈퇴한_사용자의_Refresh_Token은_USER_NOT_FOUND로_거부한다() {
        AppUser appUser = AppUser.create(APPLE_SUBJECT, "사용자");
        appUser.withdraw(Instant.parse("2026-07-09T00:00:00Z"));
        UUID tokenFamilyId = UUID.randomUUID();
        RefreshToken refreshToken =
                RefreshToken.create(
                        appUser,
                        REFRESH_TOKEN_HASH,
                        tokenFamilyId,
                        Instant.parse("2999-01-01T00:00:00Z"));
        givenRefreshTokenVerification(appUser, tokenFamilyId, processingIdempotencyRecord(appUser));
        when(refreshTokenRepository.findWithLockByTokenHash(REFRESH_TOKEN_HASH))
                .thenReturn(Optional.of(refreshToken));

        assertRefreshTokenError(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 회전된_Refresh_Token_재사용은_family_전체를_폐기하고_재사용_탐지_오류를_반환한다() {
        AppUser appUser = AppUser.create(APPLE_SUBJECT, "사용자");
        UUID tokenFamilyId = UUID.randomUUID();
        RefreshToken reusedRefreshToken =
                RefreshToken.create(
                        appUser,
                        REFRESH_TOKEN_HASH,
                        tokenFamilyId,
                        Instant.parse("2999-01-01T00:00:00Z"));
        RefreshToken activeFamilyRefreshToken =
                RefreshToken.create(
                        appUser,
                        "active-family-token-hash",
                        tokenFamilyId,
                        Instant.parse("2999-01-01T00:00:00Z"));
        reusedRefreshToken.replaceBy(activeFamilyRefreshToken);
        givenRefreshTokenVerification(appUser, tokenFamilyId, processingIdempotencyRecord(appUser));
        when(refreshTokenRepository.findWithLockByTokenHash(REFRESH_TOKEN_HASH))
                .thenReturn(Optional.of(reusedRefreshToken));
        when(refreshTokenRepository.findWithLockByAppUserIdAndTokenFamilyId(
                        appUser.getId(), tokenFamilyId))
                .thenReturn(java.util.List.of(reusedRefreshToken, activeFamilyRefreshToken));

        assertRefreshTokenError(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);

        assertThat(reusedRefreshToken.getRevokedAt()).isNotNull();
        assertThat(activeFamilyRefreshToken.getRevokedAt()).isNotNull();
    }

    @Test
    void 로그아웃은_검증된_현재_사용자_소유_Refresh_Token을_폐기한다() {
        AppUser appUser = AppUser.create(APPLE_SUBJECT, "집집이");
        RefreshToken refreshToken =
                RefreshToken.create(
                        appUser,
                        REFRESH_TOKEN_HASH,
                        UUID.randomUUID(),
                        Instant.parse("2026-07-22T00:00:00Z"));
        when(refreshTokenValidator.validateForLogout(REFRESH_TOKEN)).thenReturn(refreshToken);

        authService.logout(appUser.getId(), new LogoutRequest(REFRESH_TOKEN));

        assertThat(refreshToken.getRevokedAt()).isNotNull();
    }

    @Test
    void 이미_폐기된_현재_사용자_소유_Refresh_Token으로_로그아웃하면_성공한다() {
        AppUser appUser = AppUser.create(APPLE_SUBJECT, "집집이");
        Instant revokedAt = Instant.parse("2026-07-09T00:00:00Z");
        RefreshToken refreshToken =
                RefreshToken.create(
                        appUser,
                        REFRESH_TOKEN_HASH,
                        UUID.randomUUID(),
                        Instant.parse("2026-07-22T00:00:00Z"));
        refreshToken.revoke(revokedAt);
        when(refreshTokenValidator.validateForLogout(REFRESH_TOKEN)).thenReturn(refreshToken);

        authService.logout(appUser.getId(), new LogoutRequest(REFRESH_TOKEN));

        assertThat(refreshToken.getRevokedAt()).isEqualTo(revokedAt);
    }

    @Test
    void 다른_사용자_소유_Refresh_Token으로_로그아웃하면_예외가_발생한다() {
        AppUser owner = AppUser.create(APPLE_SUBJECT, "집집이");
        RefreshToken refreshToken =
                RefreshToken.create(
                        owner,
                        REFRESH_TOKEN_HASH,
                        UUID.randomUUID(),
                        Instant.parse("2026-07-22T00:00:00Z"));
        when(refreshTokenValidator.validateForLogout(REFRESH_TOKEN)).thenReturn(refreshToken);

        assertThatThrownBy(
                        () ->
                                authService.logout(
                                        UUID.randomUUID(), new LogoutRequest(REFRESH_TOKEN)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }

    private void givenAppleVerification(String appleSubject) {
        when(appleIdTokenVerifier.verify(IDENTITY_TOKEN, NONCE))
                .thenReturn(new AppleUserInfo(appleSubject, null));
        when(appleTokenClient.requestToken(AUTHORIZATION_CODE)).thenReturn(tokenResponse());
        when(appleIdTokenVerifier.verify(TOKEN_RESPONSE_ID_TOKEN, NONCE))
                .thenReturn(new AppleUserInfo(appleSubject, null));
    }

    private void givenTokenIssue() {
        when(jwtTokenProvider.generateAccessToken(any(UUID.class))).thenReturn(ACCESS_TOKEN);
        when(jwtTokenProvider.generateRefreshToken(any(UUID.class), any(UUID.class)))
                .thenReturn(REFRESH_TOKEN);
        when(jwtTokenProvider.getAccessTokenExpiresIn()).thenReturn(1800L);
        when(jwtTokenProvider.getRefreshTokenExpiresAt())
                .thenReturn(Instant.parse("2026-07-22T00:00:00Z"));
        when(refreshTokenHasher.hash(eq(REFRESH_TOKEN))).thenReturn(REFRESH_TOKEN_HASH);
    }

    private AppleTokenResponse tokenResponse() {
        return new AppleTokenResponse(null, null, TOKEN_RESPONSE_ID_TOKEN, null, null);
    }

    private void givenRefreshTokenVerification(
            AppUser appUser, UUID tokenFamilyId, ApiIdempotencyRecord idempotencyRecord) {
        givenRefreshTokenClaimsAndHash(appUser, tokenFamilyId);
        when(idempotencyService.start(
                        any(),
                        any(UUID.class),
                        any(),
                        any(),
                        eq(IDEMPOTENCY_REQUEST_HASH),
                        eq(TokenRefreshResponse.class)))
                .thenReturn(
                        new IdempotencyService.IdempotencyStart<>(idempotencyRecord, null, false));
    }

    private void givenRefreshTokenClaimsAndHash(AppUser appUser, UUID tokenFamilyId) {
        when(jwtTokenProvider.verifyRefreshToken(REFRESH_TOKEN))
                .thenReturn(
                        new JwtTokenProvider.RefreshTokenClaims(appUser.getId(), tokenFamilyId));
        when(refreshTokenHasher.hash(REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(idempotencyService.hashAuthRefreshRequest(REFRESH_TOKEN_HASH))
                .thenReturn(IDEMPOTENCY_REQUEST_HASH);
    }

    private void givenRefreshTokenRotation(RefreshToken currentRefreshToken) {
        when(refreshTokenRepository.findWithLockByTokenHash(REFRESH_TOKEN_HASH))
                .thenReturn(Optional.of(currentRefreshToken));
        when(jwtTokenProvider.generateAccessToken(currentRefreshToken.getAppUser().getId()))
                .thenReturn(ROTATED_ACCESS_TOKEN);
        when(jwtTokenProvider.generateRefreshToken(
                        currentRefreshToken.getAppUser().getId(),
                        currentRefreshToken.getTokenFamilyId()))
                .thenReturn(ROTATED_REFRESH_TOKEN);
        when(jwtTokenProvider.getAccessTokenExpiresIn()).thenReturn(1800L);
        when(jwtTokenProvider.getRefreshTokenExpiresAt())
                .thenReturn(Instant.parse("2999-01-15T00:00:00Z"));
        when(refreshTokenHasher.hash(ROTATED_REFRESH_TOKEN)).thenReturn(ROTATED_REFRESH_TOKEN_HASH);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ApiIdempotencyRecord processingIdempotencyRecord(AppUser appUser) {
        return ApiIdempotencyRecord.processing(
                "AUTH_REFRESH:" + appUser.getId(),
                UUID.fromString(IDEMPOTENCY_KEY),
                "POST",
                "/api/v1/auth/refresh",
                IDEMPOTENCY_REQUEST_HASH,
                Instant.parse("2999-01-01T00:00:00Z"));
    }

    private void assertRefreshTokenError(org.zipzip.zipzipserver.global.code.ErrorCode errorCode) {
        assertThatThrownBy(
                        () ->
                                authService.refreshTokens(
                                        new TokenRefreshRequest(REFRESH_TOKEN), IDEMPOTENCY_KEY))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
