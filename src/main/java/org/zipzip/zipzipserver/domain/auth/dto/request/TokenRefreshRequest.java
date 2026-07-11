package org.zipzip.zipzipserver.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "토큰 갱신 요청")
public record TokenRefreshRequest(
        @Schema(
                        description =
                                "직전 로그인 또는 토큰 갱신 응답에서 받은 서버 발급 Refresh Token 원문. 토큰 갱신이"
                                        + " 성공하면 이 값은 즉시 폐기되므로, 응답의 새 refreshToken으로 교체해야 합니다.",
                        format = "JWT",
                        example = "eyJhbGciOiJIUzI1NiJ9...",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String refreshToken) {}
