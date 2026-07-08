package org.zipzip.zipzipserver.domain.auth.jwt;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
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
import org.zipzip.zipzipserver.domain.auth.config.JwtProperties;

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
                appUserId, properties.getAccessTokenExpiration(), ACCESS_TOKEN_TYPE, null);
    }

    public String generateRefreshToken(UUID appUserId, UUID tokenFamilyId) {
        return generateToken(
                appUserId,
                properties.getRefreshTokenExpiration(),
                REFRESH_TOKEN_TYPE,
                tokenFamilyId);
    }

    public long getAccessTokenExpiresIn() {
        return properties.getAccessTokenExpiration().toSeconds();
    }

    public Instant getRefreshTokenExpiresAt() {
        return Instant.now(clock).plus(properties.getRefreshTokenExpiration());
    }

    private String generateToken(
            UUID appUserId, Duration expiration, String tokenType, UUID tokenFamilyId) {
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
            signedJWT.sign(new MACSigner(properties.getSecret().getBytes(StandardCharsets.UTF_8)));

            return signedJWT.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("JWT 발급에 실패했습니다.", exception);
        }
    }

    private JWSHeader createHeader() {
        return new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build();
    }
}
