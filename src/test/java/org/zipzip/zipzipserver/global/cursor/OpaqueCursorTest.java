package org.zipzip.zipzipserver.global.cursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.zipzip.zipzipserver.domain.photo.code.PhotoErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;

class OpaqueCursorTest {

    @Test
    void 인코딩한_cursor를_디코딩하면_원래_값을_복원한다() {
        Instant timestamp = Instant.parse("2026-06-30T04:20:00.123456Z");
        UUID id = UUID.randomUUID();

        String cursor = OpaqueCursor.encode(timestamp, id);
        OpaqueCursor.Decoded decoded = OpaqueCursor.decode(cursor, PhotoErrorCode.INVALID_CURSOR);

        assertThat(decoded.timestamp()).isEqualTo(timestamp);
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    void 형식이_잘못된_cursor는_지정한_에러코드_예외가_발생한다() {
        assertThatThrownBy(
                        () ->
                                OpaqueCursor.decode(
                                        "not-a-valid-cursor!!", PhotoErrorCode.INVALID_CURSOR))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(PhotoErrorCode.INVALID_CURSOR));
    }
}
