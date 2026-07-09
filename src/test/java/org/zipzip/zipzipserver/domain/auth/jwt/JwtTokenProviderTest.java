package org.zipzip.zipzipserver.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zipzip.zipzipserver.domain.auth.code.AuthErrorCode;
import org.zipzip.zipzipserver.domain.auth.config.JwtProperties;
import org.zipzip.zipzipserver.global.code.GlobalErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;

class JwtTokenProviderTest {

    private static final String ACCESS_SECRET = "test-access-secret-must-be-at-least-32-characters";
    private static final String REFRESH_SECRET =
            "test-refresh-secret-must-be-at-least-32-characters";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("zipzip-server-test");
        properties.setAccessSecret(ACCESS_SECRET);
        properties.setRefreshSecret(REFRESH_SECRET);
        properties.setAccessTokenExpiration(Duration.ofMinutes(30));
        properties.setRefreshTokenExpiration(Duration.ofDays(14));
        jwtTokenProvider = new JwtTokenProvider(properties);
    }

    @Test
    void Refresh_Token을_검증하면_사용자와_토큰_family_id를_반환한다() {
        UUID appUserId = UUID.randomUUID();
        UUID tokenFamilyId = UUID.randomUUID();
        String refreshToken = jwtTokenProvider.generateRefreshToken(appUserId, tokenFamilyId);

        JwtTokenProvider.RefreshTokenClaims claims =
                jwtTokenProvider.verifyRefreshToken(refreshToken);

        assertThat(claims.appUserId()).isEqualTo(appUserId);
        assertThat(claims.tokenFamilyId()).isEqualTo(tokenFamilyId);
    }

    @Test
    void Access_Token을_검증하면_사용자_id를_반환한다() {
        UUID appUserId = UUID.randomUUID();
        String accessToken = jwtTokenProvider.generateAccessToken(appUserId);

        UUID verifiedAppUserId = jwtTokenProvider.verifyAccessToken(accessToken);

        assertThat(verifiedAppUserId).isEqualTo(appUserId);
    }

    @Test
    void Access_Token은_Refresh_Token으로_검증할_수_없다() {
        String accessToken = jwtTokenProvider.generateAccessToken(UUID.randomUUID());

        assertThatThrownBy(() -> jwtTokenProvider.verifyRefreshToken(accessToken))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    void Refresh_Token은_Access_Token으로_검증할_수_없다() {
        String refreshToken =
                jwtTokenProvider.generateRefreshToken(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> jwtTokenProvider.verifyAccessToken(refreshToken))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(GlobalErrorCode.UNAUTHORIZED));
    }

    @Test
    void 서명이_다른_Access_Token은_거부한다() {
        JwtTokenProvider otherJwtTokenProvider = new JwtTokenProvider(jwtProperties("other"));
        String accessToken = otherJwtTokenProvider.generateAccessToken(UUID.randomUUID());

        assertThatThrownBy(() -> jwtTokenProvider.verifyAccessToken(accessToken))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(GlobalErrorCode.UNAUTHORIZED));
    }

    @Test
    void 형식이_잘못된_Access_Token은_거부한다() {
        assertThatThrownBy(() -> jwtTokenProvider.verifyAccessToken("not-a-jwt"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(GlobalErrorCode.UNAUTHORIZED));
    }

    @Test
    void 만료된_Access_Token은_거부한다() {
        JwtTokenProvider expiredJwtTokenProvider = new JwtTokenProvider(jwtProperties("expired"));
        String accessToken = expiredJwtTokenProvider.generateAccessToken(UUID.randomUUID());

        assertThatThrownBy(() -> expiredJwtTokenProvider.verifyAccessToken(accessToken))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(GlobalErrorCode.UNAUTHORIZED));
    }

    private JwtProperties jwtProperties(String profile) {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("zipzip-server-test");
        properties.setAccessSecret(
                "test-access-secret-" + profile + "-must-be-at-least-32-characters");
        properties.setRefreshSecret(REFRESH_SECRET);
        properties.setAccessTokenExpiration(
                "expired".equals(profile) ? Duration.ofSeconds(-1) : Duration.ofMinutes(30));
        properties.setRefreshTokenExpiration(Duration.ofDays(14));
        return properties;
    }
}
