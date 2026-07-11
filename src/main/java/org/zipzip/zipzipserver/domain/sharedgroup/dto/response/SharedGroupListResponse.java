package org.zipzip.zipzipserver.domain.sharedgroup.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "내 공유 그룹 목록 커서 페이지 응답")
public record SharedGroupListResponse(
        @Schema(description = "현재 페이지의 공유 그룹 목록") List<SharedGroupSummaryResponse> items,
        @Schema(description = "다음 페이지 조회에 그대로 전달할 불투명 커서. 마지막 페이지면 null", nullable = true) String nextCursor,
        @Schema(description = "다음 페이지 존재 여부", example = "false") boolean hasNext) {}
