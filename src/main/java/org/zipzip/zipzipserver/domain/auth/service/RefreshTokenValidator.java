package org.zipzip.zipzipserver.domain.auth.service;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zipzip.zipzipserver.domain.auth.code.AuthErrorCode;
import org.zipzip.zipzipserver.domain.auth.entity.RefreshToken;
import org.zipzip.zipzipserver.domain.auth.jwt.JwtTokenProvider;
import org.zipzip.zipzipserver.domain.auth.repository.RefreshTokenRepository;
import org.zipzip.zipzipserver.domain.auth.token.RefreshTokenHasher;
import org.zipzip.zipzipserver.global.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class RefreshTokenValidator {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenHasher refreshTokenHasher;
    private final Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public RefreshToken validate(String refreshToken) {
        return validate(refreshToken, false);
    }

    @Transactional(readOnly = true)
    public RefreshToken validateForLogout(String refreshToken) {
        return validate(refreshToken, true);
    }

    private RefreshToken validate(String refreshToken, boolean allowRevokedToken) {
        JwtTokenProvider.RefreshTokenClaims claims =
                jwtTokenProvider.verifyRefreshToken(refreshToken);
        RefreshToken refreshTokenEntity =
                refreshTokenRepository
                        .findByTokenHash(refreshTokenHasher.hash(refreshToken))
                        .orElseThrow(
                                () -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        validateStoredRefreshToken(refreshTokenEntity, claims, allowRevokedToken);

        return refreshTokenEntity;
    }

    private void validateStoredRefreshToken(
            RefreshToken refreshToken,
            JwtTokenProvider.RefreshTokenClaims claims,
            boolean allowRevokedToken) {
        if (!refreshToken.getAppUser().getId().equals(claims.appUserId())) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (!refreshToken.getTokenFamilyId().equals(claims.tokenFamilyId())) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (refreshToken.getReplacedByRefreshToken() != null) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        }

        if (!allowRevokedToken && refreshToken.getRevokedAt() != null) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        }

        if (!refreshToken.getExpiresAt().isAfter(Instant.now(clock))) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        }
    }
}
