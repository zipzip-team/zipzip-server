package org.zipzip.zipzipserver.domain.sharedgroup.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "공유 그룹 생성 요청")
public record CreateSharedGroupRequest(
        @Schema(
                        description = "생성할 공유 그룹 이름. 서버가 앞뒤 공백을 제거한 뒤 1~100자인지 검증합니다.",
                        example = "우리 집",
                        minLength = 1,
                        maxLength = 100,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "공유 그룹 이름은 필수입니다.")
                @Size(max = 100, message = "공유 그룹 이름은 100자 이하여야 합니다.")
                String name) {}
