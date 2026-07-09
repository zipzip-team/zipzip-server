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
public class SharedGroupMemberCursorCodec {

    private static final String SCOPE = "sharedGroupMembers:v1";

    private final ObjectMapper objectMapper;

    public SharedGroupMemberCursor decode(String cursor) {
        if (cursor == null) {
            return null;
        }

        if (cursor.isBlank()) {
            throw new BusinessException(GlobalErrorCode.INVALID_CURSOR);
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            CursorPayload payload =
                    objectMapper.readValue(
                            new String(decoded, StandardCharsets.UTF_8), CursorPayload.class);
            if (!SCOPE.equals(payload.scope())
                    || payload.joinedAt() == null
                    || payload.membershipId() == null) {
                throw new IllegalArgumentException("커서 payload가 올바르지 않습니다.");
            }

            return new SharedGroupMemberCursor(
                    Instant.parse(payload.joinedAt()), UUID.fromString(payload.membershipId()));
        } catch (Exception exception) {
            throw new BusinessException(GlobalErrorCode.INVALID_CURSOR);
        }
    }

    public String encode(SharedGroupMemberCursor cursor) {
        try {
            CursorPayload payload =
                    new CursorPayload(
                            SCOPE, cursor.joinedAt().toString(), cursor.membershipId().toString());
            byte[] json = objectMapper.writeValueAsBytes(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception exception) {
            throw new IllegalStateException("공유 그룹 멤버 커서 생성에 실패했습니다.", exception);
        }
    }

    private record CursorPayload(String scope, String joinedAt, String membershipId) {}
}
