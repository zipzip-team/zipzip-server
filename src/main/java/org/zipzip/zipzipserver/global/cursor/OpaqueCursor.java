package org.zipzip.zipzipserver.global.cursor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.zipzip.zipzipserver.global.code.ErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;

/**
 * {@code timestamp epochMilli:id}를 Base64Url로 감싼 불투명 cursor. 타임스탬프 내림차순 + id 동점 처리를 쓰는 커서 페이지네이션(예:
 * PHOTO-01, ALBUM-01)에서 공통으로 재사용한다.
 */
public final class OpaqueCursor {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private OpaqueCursor() {}

    public record Decoded(Instant timestamp, UUID id) {}

    public static String encode(Instant timestamp, UUID id) {
        String raw = timestamp.toEpochMilli() + ":" + id;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Decoded decode(String cursor, ErrorCode invalidCursorErrorCode) {
        try {
            String raw = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
            int separatorIndex = raw.indexOf(':');
            Instant timestamp =
                    Instant.ofEpochMilli(Long.parseLong(raw.substring(0, separatorIndex)));
            UUID id = UUID.fromString(raw.substring(separatorIndex + 1));
            return new Decoded(timestamp, id);
        } catch (Exception exception) {
            throw new BusinessException(invalidCursorErrorCode);
        }
    }
}
