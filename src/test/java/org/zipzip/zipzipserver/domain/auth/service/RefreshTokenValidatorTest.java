package org.zipzip.zipzipserver.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zipzip.zipzipserver.domain.auth.code.AuthErrorCode;
import org.zipzip.zipzipserver.domain.auth.entity.RefreshToken;
import org.zipzip.zipzipserver.domain.auth.jwt.JwtTokenProvider;
import org.zipzip.zipzipserver.domain.auth.repository.RefreshTokenRepository;
import org.zipzip.zipzipserver.domain.auth.token.RefreshTokenHasher;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.global.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class RefreshTokenValidatorTest {

    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String TOKEN_HASH = "token-hash";
    private static final UUID TOKEN_FAMILY_ID = UUID.randomUUID();

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private RefreshTokenHasher refreshTokenHasher;

    @InjectMocks private RefreshTokenValidator refreshTokenValidator;

    @Test
    void 유효한_Refresh_Token은_JWT와_DB_저장_상태를_함께_검증한다() {
        AppUser appUser = AppUser.create("apple-subject", "사용자");
        RefreshToken refreshToken =
                RefreshToken.create(
                        appUser,
                        TOKEN_HASH,
                        TOKEN_FAMILY_ID,
                        Instant.parse("2999-01-01T00:00:00Z"));
        givenRefreshTokenClaims(appUser);
        when(refreshTokenHasher.hash(REFRESH_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(refreshToken));

        RefreshToken validatedRefreshToken = refreshTokenValidator.validate(REFRESH_TOKEN);

        assertThat(validatedRefreshToken).isEqualTo(refreshToken);
    }

    @Test
    void DB에_해시가_없으면_Refresh_Token을_거부한다() {
        AppUser appUser = AppUser.create("apple-subject", "사용자");
        givenRefreshTokenClaims(appUser);
        when(refreshTokenHasher.hash(REFRESH_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

        assertInvalidRefreshToken();
    }

    @Test
    void 폐기된_Refresh_Token은_거부한다() {
        AppUser appUser = AppUser.create("apple-subject", "사용자");
        RefreshToken refreshToken =
                RefreshToken.create(
                        appUser,
                        TOKEN_HASH,
                        TOKEN_FAMILY_ID,
                        Instant.parse("2999-01-01T00:00:00Z"));
        refreshToken.revoke(Instant.parse("2026-07-09T00:00:00Z"));
        givenStoredRefreshToken(appUser, refreshToken);

        assertRefreshTokenError(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
    }

    @Test
    void 이미_폐기된_Refresh_Token도_로그아웃_용도로는_검증한다() {
        AppUser appUser = AppUser.create("apple-subject", "사용자");
        RefreshToken refreshToken =
                RefreshToken.create(
                        appUser,
                        TOKEN_HASH,
                        TOKEN_FAMILY_ID,
                        Instant.parse("2999-01-01T00:00:00Z"));
        refreshToken.revoke(Instant.parse("2026-07-09T00:00:00Z"));
        givenStoredRefreshToken(appUser, refreshToken);

        RefreshToken validatedRefreshToken = refreshTokenValidator.validateForLogout(REFRESH_TOKEN);

        assertThat(validatedRefreshToken).isEqualTo(refreshToken);
    }

    @Test
    void 만료된_Refresh_Token은_거부한다() {
        AppUser appUser = AppUser.create("apple-subject", "사용자");
        RefreshToken refreshToken =
                RefreshToken.create(
                        appUser,
                        TOKEN_HASH,
                        TOKEN_FAMILY_ID,
                        Instant.parse("2000-01-01T00:00:00Z"));
        givenStoredRefreshToken(appUser, refreshToken);

        assertRefreshTokenError(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
    }

    @Test
    void JWT의_토큰_family_id와_DB_토큰_family_id가_다르면_거부한다() {
        AppUser appUser = AppUser.create("apple-subject", "사용자");
        RefreshToken refreshToken =
                RefreshToken.create(
                        appUser,
                        TOKEN_HASH,
                        UUID.randomUUID(),
                        Instant.parse("2999-01-01T00:00:00Z"));
        givenStoredRefreshToken(appUser, refreshToken);

        assertInvalidRefreshToken();
    }

    private void givenStoredRefreshToken(AppUser appUser, RefreshToken refreshToken) {
        givenRefreshTokenClaims(appUser);
        when(refreshTokenHasher.hash(REFRESH_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(refreshToken));
    }

    private void givenRefreshTokenClaims(AppUser appUser) {
        when(jwtTokenProvider.verifyRefreshToken(REFRESH_TOKEN))
                .thenReturn(
                        new JwtTokenProvider.RefreshTokenClaims(appUser.getId(), TOKEN_FAMILY_ID));
    }

    private void assertInvalidRefreshToken() {
        assertRefreshTokenError(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    private void assertRefreshTokenError(AuthErrorCode authErrorCode) {
        assertThatThrownBy(() -> refreshTokenValidator.validate(REFRESH_TOKEN))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(authErrorCode));
    }
}
