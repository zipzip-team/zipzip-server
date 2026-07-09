package org.zipzip.zipzipserver.domain.sharedgroup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.zipzip.zipzipserver.global.code.GlobalErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;

class SharedGroupMemberCursorCodecTest {

    private final SharedGroupMemberCursorCodec codec =
            new SharedGroupMemberCursorCodec(new ObjectMapper());

    @Test
    void 커서를_인코딩하고_디코딩한다() {
        SharedGroupMemberCursor cursor =
                new SharedGroupMemberCursor(
                        Instant.parse("2026-07-03T10:15:30Z"),
                        UUID.fromString("00000000-0000-0000-0000-000000000001"));

        String encoded = codec.encode(cursor);
        SharedGroupMemberCursor decoded = codec.decode(encoded);

        assertThat(decoded).isEqualTo(cursor);
    }

    @Test
    void null_커서는_첫_페이지로_처리한다() {
        assertThat(codec.decode(null)).isNull();
    }

    @Test
    void 잘못된_커서는_INVALID_CURSOR_예외가_발생한다() {
        assertThatThrownBy(() -> codec.decode("not-base64"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(GlobalErrorCode.INVALID_CURSOR));
    }

    @Test
    void scope가_다른_커서는_INVALID_CURSOR_예외가_발생한다() {
        String payload =
                """
                {"scope":"other:v1","joinedAt":"2026-07-03T10:15:30Z","membershipId":"00000000-0000-0000-0000-000000000001"}
                """;
        String encoded =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> codec.decode(encoded))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(GlobalErrorCode.INVALID_CURSOR));
    }
}
