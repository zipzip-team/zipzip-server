package org.zipzip.zipzipserver.domain.sharedgroup.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;

@Schema(description = "공유 그룹 생성 응답")
public record CreateSharedGroupResponse(
        @Schema(description = "생성된 공유 그룹 식별자", example = "b8a5f612-25d7-4ec3-9d1d-59684de40664") UUID id,
        @Schema(description = "정규화된 공유 그룹 이름", example = "우리 집") String name,
        @Schema(description = "다른 사용자를 참여시킬 때 전달할 고정 초대 코드", example = "ZZ7K9P2Q") String inviteCode,
        @Schema(description = "생성 직후 요청자의 공유 그룹 역할", example = "HOST") SharedGroupRole myRole,
        @Schema(description = "공유 그룹 최초 생성자") SharedGroupUserSummaryResponse createdBy,
        @Schema(description = "공유 그룹 생성 시각(UTC ISO-8601)", example = "2026-07-03T10:15:30Z") Instant createdAt) {}
