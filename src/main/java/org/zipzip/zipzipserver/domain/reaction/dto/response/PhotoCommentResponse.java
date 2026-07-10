package org.zipzip.zipzipserver.domain.reaction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "사진 댓글 응답")
public record PhotoCommentResponse(
        @Schema(description = "댓글 식별자") UUID id,
        @Schema(description = "댓글이 속한 사진 식별자") UUID photoId,
        @Schema(description = "댓글 본문", example = "사진 너무 좋다!") String content,
        @Schema(description = "작성자 요약") Author author,
        @Schema(description = "댓글 작성 시각") Instant createdAt,
        @Schema(description = "댓글 수정 시각") Instant updatedAt) {

    @Schema(description = "댓글 작성자 요약")
    public record Author(
            @Schema(description = "사용자 식별자") UUID userId,
            @Schema(description = "사용자 표시 이름", example = "집집이") String displayName) {}
}
