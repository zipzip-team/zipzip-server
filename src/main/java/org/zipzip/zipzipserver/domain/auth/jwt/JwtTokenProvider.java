package org.zipzip.zipzipserver.domain.auth.jwt;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.zipzip.zipzipserver.domain.auth.code.AuthErrorCode;
import org.zipzip.zipzipserver.domain.auth.config.JwtProperties;
import org.zipzip.zipzipserver.global.code.GlobalErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String TOKEN_FAMILY_ID_CLAIM = "token_family_id";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final JwtProperties properties;
    private final Clock clock = Clock.systemUTC();

    public String generateAccessToken(UUID appUserId) {
        return generateToken(
                appUserId,
                properties.getAccessTokenExpiration(),
                ACCESS_TOKEN_TYPE,
                null,
                properties.getAccessSecret());
    }

    public String generateRefreshToken(UUID appUserId, UUID tokenFamilyId) {
        return generateToken(
                appUserId,
                properties.getRefreshTokenExpiration(),
                REFRESH_TOKEN_TYPE,
                tokenFamilyId,
                properties.getRefreshSecret());
    }

    public UUID verifyAccessToken(String accessToken) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(accessToken);
            validateHeader(signedJWT);

            boolean verified =
                    signedJWT.verify(
                            new MACVerifier(
                                    properties.getAccessSecret().getBytes(StandardCharsets.UTF_8)));
            if (!verified) {
                throw new IllegalArgumentException("Access Token 서명 검증에 실패했습니다.");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            validateClaims(claims, ACCESS_TOKEN_TYPE);

            return UUID.fromString(claims.getSubject());
        } catch (Exception exception) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
    }

    public RefreshTokenClaims verifyRefreshToken(String refreshToken) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(refreshToken);
            validateHeader(signedJWT);

            boolean verified =
                    signedJWT.verify(
                            new MACVerifier(
                                    properties
                                            .getRefreshSecret()
                                            .getBytes(StandardCharsets.UTF_8)));
            if (!verified) {
                throw new IllegalArgumentException("Refresh Token 서명 검증에 실패했습니다.");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            validateClaims(claims, REFRESH_TOKEN_TYPE);

            String tokenFamilyId = claims.getStringClaim(TOKEN_FAMILY_ID_CLAIM);
            if (tokenFamilyId == null || tokenFamilyId.isBlank()) {
                throw new IllegalArgumentException("Refresh Token family id가 비어 있습니다.");
            }

            return new RefreshTokenClaims(
                    UUID.fromString(claims.getSubject()), UUID.fromString(tokenFamilyId));
        } catch (Exception exception) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    public long getAccessTokenExpiresIn() {
        return properties.getAccessTokenExpiration().toSeconds();
    }

    public Instant getRefreshTokenExpiresAt() {
        return Instant.now(clock).plus(properties.getRefreshTokenExpiration());
    }

    private String generateToken(
            UUID appUserId,
            Duration expiration,
            String tokenType,
            UUID tokenFamilyId,
            String secret) {
        try {
            Instant now = Instant.now(clock);
            JWTClaimsSet.Builder claimsBuilder =
                    new JWTClaimsSet.Builder()
                            .issuer(properties.getIssuer())
                            .subject(appUserId.toString())
                            .jwtID(UUID.randomUUID().toString())
                            .issueTime(Date.from(now))
                            .expirationTime(Date.from(now.plus(expiration)))
                            .claim(TOKEN_TYPE_CLAIM, tokenType);

            if (tokenFamilyId != null) {
                claimsBuilder.claim(TOKEN_FAMILY_ID_CLAIM, tokenFamilyId.toString());
            }

            SignedJWT signedJWT = new SignedJWT(createHeader(), claimsBuilder.build());
            signedJWT.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));

            return signedJWT.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("JWT 발급에 실패했습니다.", exception);
        }
    }

    private void validateHeader(SignedJWT signedJWT) {
        JWSHeader header = signedJWT.getHeader();
        if (!JWSAlgorithm.HS256.equals(header.getAlgorithm())
                || !JOSEObjectType.JWT.equals(header.getType())) {
            throw new IllegalArgumentException("JWT 헤더가 올바르지 않습니다.");
        }
    }

    private void validateClaims(JWTClaimsSet claims, String expectedTokenType) throws Exception {
        if (!properties.getIssuer().equals(claims.getIssuer())) {
            throw new IllegalArgumentException("JWT issuer가 올바르지 않습니다.");
        }

        if (!expectedTokenType.equals(claims.getStringClaim(TOKEN_TYPE_CLAIM))) {
            throw new IllegalArgumentException("JWT token type이 올바르지 않습니다.");
        }

        Date expirationTime = claims.getExpirationTime();
        if (expirationTime == null || expirationTime.toInstant().isBefore(Instant.now(clock))) {
            throw new IllegalArgumentException("JWT가 만료되었습니다.");
        }

        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new IllegalArgumentException("JWT subject가 비어 있습니다.");
        }
    }

    private JWSHeader createHeader() {
        return new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build();
    }

    public record RefreshTokenClaims(UUID appUserId, UUID tokenFamilyId) {}
}
