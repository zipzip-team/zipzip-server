package org.zipzip.zipzipserver.domain.sharedgroup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.zipzip.zipzipserver.global.code.GlobalErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;

@Component
@RequiredArgsConstructor
public class SharedGroupCursorCodec {

    private static final int CURRENT_VERSION = 1;

    private final ObjectMapper objectMapper;

    public String encode(Instant joinedAt, UUID sharedGroupId) {
        try {
            CursorPayload payload = new CursorPayload(CURRENT_VERSION, joinedAt, sharedGroupId);
            byte[] json = objectMapper.writeValueAsBytes(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception exception) {
            throw new IllegalStateException("공유 그룹 cursor 생성에 실패했습니다.", exception);
        }
    }

    public SharedGroupCursor decode(String cursor) {
        if (cursor == null) {
            return null;
        }

        try {
            byte[] json = Base64.getUrlDecoder().decode(cursor.getBytes(StandardCharsets.UTF_8));
            CursorPayload payload = objectMapper.readValue(json, CursorPayload.class);
            if (payload.version() != CURRENT_VERSION
                    || payload.joinedAt() == null
                    || payload.sharedGroupId() == null) {
                throw new IllegalArgumentException("cursor payload가 올바르지 않습니다.");
            }
            return new SharedGroupCursor(payload.joinedAt(), payload.sharedGroupId());
        } catch (Exception exception) {
            throw new BusinessException(GlobalErrorCode.INVALID_CURSOR);
        }
    }

    private record CursorPayload(int version, Instant joinedAt, UUID sharedGroupId) {}

    public record SharedGroupCursor(Instant joinedAt, UUID sharedGroupId) {}
}
