package org.zipzip.zipzipserver.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 사용자 프로필 응답")
public record UserProfileResponse(
        @Schema(
                        description = "현재 사용자의 표시 이름",
                        example = "집집이",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String displayName) {}
