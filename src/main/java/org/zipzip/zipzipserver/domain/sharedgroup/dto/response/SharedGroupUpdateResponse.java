package org.zipzip.zipzipserver.domain.sharedgroup.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;

@Schema(description = "공유 그룹 이름 수정 응답")
public record SharedGroupUpdateResponse(
        @Schema(description = "공유 그룹 식별자", example = "b8a5f612-25d7-4ec3-9d1d-59684de40664")
                UUID id,
        @Schema(description = "정규화된 새 공유 그룹 이름", example = "여름 여행") String name,
        @Schema(description = "이름 수정 시각(UTC ISO-8601)", example = "2026-07-03T12:00:00Z")
                Instant updatedAt) {

    public static SharedGroupUpdateResponse from(SharedGroup sharedGroup) {
        return new SharedGroupUpdateResponse(
                sharedGroup.getId(), sharedGroup.getName(), sharedGroup.getUpdatedAt());
    }
}
