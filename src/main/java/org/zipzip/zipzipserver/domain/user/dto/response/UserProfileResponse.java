package org.zipzip.zipzipserver.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserProfileResponse(
        @Schema(
                        description = "사용자 이름",
                        example = "집집이",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String displayName) {}
