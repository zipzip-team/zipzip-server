package org.zipzip.zipzipserver.domain.sharedgroup.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;

@Schema(description = "공유 그룹 활성 멤버 목록 커서 페이지 응답")
public record SharedGroupMemberListResponse(
        @Schema(description = "현재 페이지의 활성 멤버 목록") List<Member> items,
        @Schema(description = "다음 페이지 조회에 그대로 전달할 불투명 커서. 마지막 페이지면 null", nullable = true) String nextCursor,
        @Schema(description = "다음 페이지 존재 여부", example = "false") boolean hasNext) {

    @Schema(description = "공유 그룹 멤버 항목")
    public record Member(
            @Schema(description = "멤버 사용자 식별자", example = "018f0c3e-2c77-7d72-a37e-2f5666f25d32") UUID userId,
            @Schema(description = "멤버 표시 이름", example = "집집이") String displayName,
            @Schema(description = "멤버의 현재 그룹 역할", example = "HOST") SharedGroupRole role,
            @Schema(description = "요청자 본인인지 여부", example = "true") boolean isMe,
            @Schema(description = "그룹 참여 시각(UTC ISO-8601)", example = "2026-07-03T10:15:30Z") Instant joinedAt) {}
}
