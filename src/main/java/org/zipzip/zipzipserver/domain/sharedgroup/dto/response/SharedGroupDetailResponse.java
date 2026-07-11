package org.zipzip.zipzipserver.domain.sharedgroup.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;

@Schema(description = "공유 그룹 상세 응답")
public record SharedGroupDetailResponse(
        @Schema(description = "공유 그룹 식별자", example = "b8a5f612-25d7-4ec3-9d1d-59684de40664")
                UUID id,
        @Schema(description = "공유 그룹 이름", example = "우리 집") String name,
        @Schema(description = "요청자의 현재 공유 그룹 역할", example = "MEMBER") SharedGroupRole myRole,
        @Schema(description = "공유 그룹 최초 생성자") SharedGroupUserSummaryResponse createdBy,
        @Schema(description = "활성 멤버 수", example = "4") long memberCount,
        @Schema(description = "활성 공유집 수", example = "3") long sharedAlbumCount,
        @Schema(description = "활성 사진 수", example = "128") long photoCount,
        @Schema(description = "공유 그룹 생성 시각(UTC ISO-8601)", example = "2026-07-03T10:15:30Z")
                Instant createdAt,
        @Schema(description = "공유 그룹 마지막 수정 시각(UTC ISO-8601)", example = "2026-07-03T10:15:30Z")
                Instant updatedAt) {}
