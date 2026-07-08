package org.zipzip.zipzipserver.domain.auth.apple;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.zipzip.zipzipserver.domain.auth.config.AppleOAuthProperties;

@Component
@RequiredArgsConstructor
public class AppleClientSecretGenerator {

    private static final String APPLE_AUDIENCE = "https://appleid.apple.com";
    private static final Duration CLIENT_SECRET_TTL = Duration.ofMinutes(30);

    private final AppleOAuthProperties properties;
    private final Clock clock = Clock.systemUTC();

    public String generate() {
        try {
            Instant now = Instant.now(clock);

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .keyID(properties.getKeyId())
                    .type(JOSEObjectType.JWT)
                    .build();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(properties.getTeamId())
                    .subject(properties.getClientId())
                    .audience(APPLE_AUDIENCE)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(CLIENT_SECRET_TTL)))
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(new ECDSASigner(parsePrivateKey(properties.normalizedPrivateKey())));

            return signedJWT.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Apple client secret 생성에 실패했습니다.", exception);
        }
    }

    private ECPrivateKey parsePrivateKey(String privateKey) throws Exception {
        String privateKeyContent = privateKey
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decodedKey = Base64.getDecoder().decode(privateKeyContent);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");

        return (ECPrivateKey) keyFactory.generatePrivate(keySpec);
    }
}
