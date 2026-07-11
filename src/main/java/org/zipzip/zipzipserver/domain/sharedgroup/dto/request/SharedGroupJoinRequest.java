package org.zipzip.zipzipserver.domain.sharedgroup.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "공유 그룹 참여 요청")
public record SharedGroupJoinRequest(
        @Schema(
                        description = "참여할 활성 공유 그룹의 초대 코드. 앞뒤 공백을 제거한 뒤 1~64자인지 검증합니다.",
                        example = "ABC234EF",
                        minLength = 1,
                        maxLength = 64,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String inviteCode) {}
