package org.zipzip.zipzipserver.global.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IdempotencyCrypto {

    private static final String AES = "AES";
    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_NONCE_BYTES = 12;
    private static final String ENCODED_PART_SEPARATOR = ":";
    private static final String HASH_ALGORITHM = "SHA-256";

    private final IdempotencyProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plainText) {
        try {
            byte[] nonce = new byte[GCM_NONCE_BYTES];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(secretKey(), AES),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)
                    + ENCODED_PART_SEPARATOR
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (Exception exception) {
            throw new IllegalStateException("멱등성 응답 암호화에 실패했습니다.", exception);
        }
    }

    public String decrypt(String encryptedText) {
        try {
            String[] parts = encryptedText.split(ENCODED_PART_SEPARATOR, 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("암호문 형식이 올바르지 않습니다.");
            }

            byte[] nonce = Base64.getUrlDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getUrlDecoder().decode(parts[1]);

            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(secretKey(), AES),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));

            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("멱등성 응답 복호화에 실패했습니다.", exception);
        }
    }

    private byte[] secretKey() throws Exception {
        return MessageDigest.getInstance(HASH_ALGORITHM)
                .digest(properties.getAuthResponseEncryptionKey().getBytes(StandardCharsets.UTF_8));
    }
}
