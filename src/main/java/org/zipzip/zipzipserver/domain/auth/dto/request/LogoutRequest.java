package org.zipzip.zipzipserver.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그아웃 요청")
public record LogoutRequest(
        @Schema(
                        description =
                                "Authorization 헤더의 Access Token과 같은 사용자에게 발급된 현재 세션의 Refresh Token."
                                        + " 이 토큰만 폐기하며 다른 기기의 토큰은 유지합니다.",
                        format = "JWT",
                        example = "eyJhbGciOiJIUzI1NiJ9...",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String refreshToken) {}
