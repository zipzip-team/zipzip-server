package org.zipzip.zipzipserver.domain.sharedgroup.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "공유 그룹 사용자 요약")
public record SharedGroupUserSummaryResponse(
        @Schema(description = "사용자 식별자", example = "018f0c3e-2c77-7d72-a37e-2f5666f25d32")
                UUID userId,
        @Schema(description = "사용자 표시 이름", example = "집집이") String displayName) {}
