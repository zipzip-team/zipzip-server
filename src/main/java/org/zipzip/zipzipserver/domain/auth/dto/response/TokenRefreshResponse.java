package org.zipzip.zipzipserver.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 갱신 응답")
public record TokenRefreshResponse(
        @Schema(
                        description =
                                "새로 발급한 Access Token. 인증 API의 Authorization 헤더에 Bearer 접두사와 함께"
                                        + " 넣습니다.",
                        format = "JWT",
                        example = "eyJhbGciOiJIUzI1NiJ9...")
                String accessToken,
        @Schema(
                        description =
                                "새로 발급한 Refresh Token. 요청에 사용한 기존 Refresh Token은 폐기되므로 반드시 이 값으로"
                                        + " 교체합니다.",
                        format = "JWT",
                        example = "eyJhbGciOiJIUzI1NiJ9...")
                String refreshToken,
        @Schema(description = "Access Token을 Authorization 헤더에 붙일 인증 방식", example = "Bearer")
                String tokenType,
        @Schema(description = "새 Access Token의 남은 유효 시간(초)", example = "1800", minimum = "1")
                long expiresIn) {}
