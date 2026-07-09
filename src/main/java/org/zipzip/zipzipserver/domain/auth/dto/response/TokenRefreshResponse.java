package org.zipzip.zipzipserver.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 갱신 응답")
public record TokenRefreshResponse(
        @Schema(description = "새로 발급한 Access Token") String accessToken,
        @Schema(description = "새로 발급한 Refresh Token. 기존 Refresh Token을 대체합니다.") String refreshToken,
        @Schema(description = "토큰 타입", example = "Bearer") String tokenType,
        @Schema(description = "Access Token 만료까지 남은 시간(초)", example = "1800") long expiresIn) {}
