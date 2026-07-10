package org.zipzip.zipzipserver.domain.reaction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "사진 댓글 목록 조회 응답")
public record PhotoCommentListResponse(
        @Schema(description = "작성일시 오름차순 댓글 목록") List<Item> items,
        @Schema(description = "다음 페이지 cursor. 없으면 마지막 페이지") String nextCursor,
        @Schema(description = "다음 페이지 존재 여부") boolean hasNext) {

    @Schema(description = "사진 댓글 목록 항목")
    public record Item(
            @Schema(description = "댓글 식별자") java.util.UUID id,
            @Schema(description = "댓글 본문", example = "사진 너무 좋다!") String content,
            @Schema(description = "작성자 요약") PhotoCommentResponse.Author author,
            @Schema(description = "요청자가 작성자인지 여부") boolean isAuthor,
            @Schema(description = "댓글 작성 시각") java.time.Instant createdAt,
            @Schema(description = "댓글 수정 시각") java.time.Instant updatedAt) {}
}
