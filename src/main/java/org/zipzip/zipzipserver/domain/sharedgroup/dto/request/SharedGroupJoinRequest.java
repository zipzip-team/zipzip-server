package org.zipzip.zipzipserver.domain.sharedgroup.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SharedGroupJoinRequest(
        @Schema(
                        description = "공유 그룹 초대 코드",
                        example = "ABC234EF",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String inviteCode) {}
