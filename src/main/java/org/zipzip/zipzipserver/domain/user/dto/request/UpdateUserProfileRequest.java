package org.zipzip.zipzipserver.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateUserProfileRequest(
        @Schema(
                        description = "변경할 사용자 이름. trim 후 1~50자",
                        example = "새 집집이",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String displayName) {}
