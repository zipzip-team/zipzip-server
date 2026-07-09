package org.zipzip.zipzipserver.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "토큰 갱신 요청")
public record TokenRefreshRequest(
        @Schema(
                        description = "서버가 발급한 Refresh Token 원문",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String refreshToken) {}
