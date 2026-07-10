package org.zipzip.zipzipserver.domain.photo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "사진 메타데이터 수정 응답")
public record PhotoMetadataUpdateResponse(
        @Schema(description = "사진 식별자") UUID id,
        @Schema(description = "수정 후 촬영일시. 제거됐으면 null") Instant takenAt,
        @Schema(description = "표시·정렬 기준 시각(takenAt이 없으면 createdAt)") Instant displayAt,
        @Schema(description = "수정 후 위도. 제거됐으면 null") Double latitude,
        @Schema(description = "수정 후 경도. 제거됐으면 null") Double longitude,
        @Schema(description = "수정 후 위치명. 제거됐으면 null") String locationName,
        @Schema(description = "위치정보 추론 여부") boolean isInferred,
        @Schema(description = "수정 시각") Instant updatedAt) {}
