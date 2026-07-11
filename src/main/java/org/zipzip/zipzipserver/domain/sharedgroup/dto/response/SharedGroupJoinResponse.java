package org.zipzip.zipzipserver.domain.sharedgroup.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;

@Schema(description = "공유 그룹 참여 응답")
public record SharedGroupJoinResponse(
        @Schema(description = "참여한 공유 그룹 식별자", example = "b8a5f612-25d7-4ec3-9d1d-59684de40664")
                UUID sharedGroupId,
        @Schema(description = "참여한 공유 그룹 이름", example = "우리 집") String name,
        @Schema(description = "참여 후 요청자의 그룹 역할", example = "MEMBER") SharedGroupRole myRole,
        @Schema(description = "멤버십 생성 시각(UTC ISO-8601)", example = "2026-07-03T10:15:30Z")
                Instant joinedAt) {}
