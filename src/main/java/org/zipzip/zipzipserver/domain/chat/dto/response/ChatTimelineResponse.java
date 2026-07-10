package org.zipzip.zipzipserver.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "그룹 채팅 타임라인 조회 응답")
public record ChatTimelineResponse(
        @Schema(description = "최신순 타임라인 항목") List<ChatTimelineItemResponse> items,
        @Schema(description = "다음 페이지 조회에 사용할 불투명 cursor. 다음 페이지가 없으면 null입니다.", nullable = true)
                String nextCursor,
        @Schema(description = "다음 페이지 존재 여부", example = "false") boolean hasNext) {}
