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
import org.zipzip.zipzipserver.domain.auth.dto.response.LoginResponse;
import org.zipzip.zipzipserver.domain.auth.entity.RefreshToken;
import org.zipzip.zipzipserver.domain.auth.jwt.JwtTokenProvider;
import org.zipzip.zipzipserver.domain.auth.repository.RefreshTokenRepository;
import org.zipzip.zipzipserver.domain.auth.token.RefreshTokenHasher;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.domain.user.repository.AppUserRepository;
import org.zipzip.zipzipserver.global.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String IDENTITY_TOKEN = "identity-token";
    private static final String AUTHORIZATION_CODE = "authorization-code";
    private static final String TOKEN_RESPONSE_ID_TOKEN = "token-response-id-token";
    private static final String APPLE_SUBJECT = "apple-subject";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String REFRESH_TOKEN_HASH = "refresh-token-hash";

    @Mock private AppleIdTokenVerifier appleIdTokenVerifier;
    @Mock private AppleTokenClient appleTokenClient;
    @Mock private AppUserRepository appUserRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenHasher refreshTokenHasher;

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
                        new AppleLoginRequest(IDENTITY_TOKEN, AUTHORIZATION_CODE, " 집집이 "));

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
                        new AppleLoginRequest(IDENTITY_TOKEN, AUTHORIZATION_CODE, " 집집이 "));

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
                        new AppleLoginRequest(IDENTITY_TOKEN, AUTHORIZATION_CODE, null));

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
                        new AppleLoginRequest(IDENTITY_TOKEN, AUTHORIZATION_CODE, " 복구 사용자 "));

        assertThat(response.isNewUser()).isFalse();
        assertThat(response.isRestoredUser()).isTrue();
        assertThat(response.user().displayName()).isEqualTo("복구 사용자");
        assertThat(appUser.isDeleted()).isFalse();
    }

    @Test
    void 신규_사용자_표시_이름이_없으면_예외가_발생한다() {
        when(appleIdTokenVerifier.verify(IDENTITY_TOKEN))
                .thenReturn(new AppleUserInfo(APPLE_SUBJECT, null));
        when(appUserRepository.findByAppleSubject(APPLE_SUBJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                authService.loginWithApple(
                                        new AppleLoginRequest(
                                                IDENTITY_TOKEN, AUTHORIZATION_CODE, null)))
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
        when(appleIdTokenVerifier.verify(IDENTITY_TOKEN))
                .thenReturn(new AppleUserInfo(APPLE_SUBJECT, null));
        when(appUserRepository.findByAppleSubject(APPLE_SUBJECT)).thenReturn(Optional.of(appUser));

        assertThatThrownBy(
                        () ->
                                authService.loginWithApple(
                                        new AppleLoginRequest(
                                                IDENTITY_TOKEN, AUTHORIZATION_CODE, null)))
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
        when(appleIdTokenVerifier.verify(IDENTITY_TOKEN))
                .thenReturn(new AppleUserInfo("request-subject", null));
        when(appUserRepository.findByAppleSubject("request-subject")).thenReturn(Optional.empty());
        when(appleTokenClient.requestToken(AUTHORIZATION_CODE)).thenReturn(tokenResponse());
        when(appleIdTokenVerifier.verify(TOKEN_RESPONSE_ID_TOKEN))
                .thenReturn(new AppleUserInfo("token-subject", null));

        assertThatThrownBy(
                        () ->
                                authService.loginWithApple(
                                        new AppleLoginRequest(
                                                IDENTITY_TOKEN, AUTHORIZATION_CODE, "집집이")))
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
                new AppleLoginRequest(IDENTITY_TOKEN, AUTHORIZATION_CODE, "집집이"));

        verify(refreshTokenHasher).hash(REFRESH_TOKEN);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        assertThat(refreshTokenCaptor.getValue().getTokenHash()).isEqualTo(REFRESH_TOKEN_HASH);
        assertThat(refreshTokenCaptor.getValue().getTokenFamilyId()).isNotNull();
    }

    private void givenAppleVerification(String appleSubject) {
        when(appleIdTokenVerifier.verify(IDENTITY_TOKEN))
                .thenReturn(new AppleUserInfo(appleSubject, null));
        when(appleTokenClient.requestToken(AUTHORIZATION_CODE)).thenReturn(tokenResponse());
        when(appleIdTokenVerifier.verify(TOKEN_RESPONSE_ID_TOKEN))
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
}
