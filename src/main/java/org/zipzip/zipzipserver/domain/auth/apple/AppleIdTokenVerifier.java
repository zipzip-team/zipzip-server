package org.zipzip.zipzipserver.domain.auth.apple;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.time.Instant;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.zipzip.zipzipserver.domain.auth.code.AuthErrorCode;
import org.zipzip.zipzipserver.domain.auth.config.AppleOAuthProperties;
import org.zipzip.zipzipserver.global.exception.BusinessException;

@Component
@RequiredArgsConstructor
public class AppleIdTokenVerifier {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";
    private static final int APPLE_JWKS_CONNECT_TIMEOUT_MILLIS = 3_000;
    private static final int APPLE_JWKS_READ_TIMEOUT_MILLIS = 5_000;
    private static final int APPLE_JWKS_SIZE_LIMIT_BYTES = 50 * 1024;

    private final AppleOAuthProperties properties;

    public AppleUserInfo verify(String idToken, String expectedNonce) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(idToken);
            validateAlgorithm(signedJWT);
            validateSignature(signedJWT);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            validateClaims(claims, expectedNonce);

            return new AppleUserInfo(claims.getSubject(), claims.getStringClaim("email"));
        } catch (Exception exception) {
            throw new BusinessException(AuthErrorCode.INVALID_APPLE_TOKEN);
        }
    }

    private void validateAlgorithm(SignedJWT signedJWT) {
        if (!JWSAlgorithm.RS256.equals(signedJWT.getHeader().getAlgorithm())) {
            throw new IllegalArgumentException("Apple id_token 서명 알고리즘이 올바르지 않습니다.");
        }
    }

    private void validateSignature(SignedJWT signedJWT) throws Exception {
        String keyId = signedJWT.getHeader().getKeyID();
        JWKSet jwkSet =
                JWKSet.load(
                        URI.create(APPLE_JWKS_URL).toURL(),
                        APPLE_JWKS_CONNECT_TIMEOUT_MILLIS,
                        APPLE_JWKS_READ_TIMEOUT_MILLIS,
                        APPLE_JWKS_SIZE_LIMIT_BYTES);
        JWK jwk = jwkSet.getKeyByKeyId(keyId);

        if (jwk == null) {
            throw new IllegalArgumentException("Apple 공개키를 찾을 수 없습니다.");
        }

        RSAKey rsaKey = jwk.toRSAKey();
        boolean verified = signedJWT.verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()));

        if (!verified) {
            throw new IllegalArgumentException("Apple id_token 서명 검증에 실패했습니다.");
        }
    }

    private void validateClaims(JWTClaimsSet claims, String expectedNonce) throws Exception {
        if (!APPLE_ISSUER.equals(claims.getIssuer())) {
            throw new IllegalArgumentException("Apple id_token issuer가 올바르지 않습니다.");
        }

        if (!claims.getAudience().contains(properties.getClientId())) {
            throw new IllegalArgumentException("Apple id_token audience가 올바르지 않습니다.");
        }

        Date expirationTime = claims.getExpirationTime();
        if (expirationTime == null || expirationTime.toInstant().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Apple id_token이 만료되었습니다.");
        }

        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new IllegalArgumentException("Apple id_token subject가 비어 있습니다.");
        }

        String nonce = claims.getStringClaim("nonce");
        if (expectedNonce == null || expectedNonce.isBlank() || !expectedNonce.equals(nonce)) {
            throw new IllegalArgumentException("Apple id_token nonce가 올바르지 않습니다.");
        }
    }
}
