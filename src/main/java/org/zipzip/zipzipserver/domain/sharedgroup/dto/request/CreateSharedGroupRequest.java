package org.zipzip.zipzipserver.domain.sharedgroup.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSharedGroupRequest(
        @Schema(
                        description = "공유 그룹 이름",
                        example = "우리 집",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "공유 그룹 이름은 필수입니다.")
                @Size(max = 100, message = "공유 그룹 이름은 100자 이하여야 합니다.")
                String name) {}
