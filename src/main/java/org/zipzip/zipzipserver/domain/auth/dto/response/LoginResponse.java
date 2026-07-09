package org.zipzip.zipzipserver.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Apple 로그인 응답")
public record LoginResponse(
        @Schema(description = "API 인증에 사용할 Access Token") String accessToken,
        @Schema(description = "토큰 갱신에 사용할 Refresh Token") String refreshToken,
        @Schema(description = "토큰 타입", example = "Bearer") String tokenType,
        @Schema(description = "Access Token 만료까지 남은 시간(초)", example = "1800") long expiresIn,
        @Schema(description = "신규 가입 여부", example = "true") boolean isNewUser,
        @Schema(description = "탈퇴 사용자 복구 여부", example = "false") boolean isRestoredUser,
        @Schema(description = "로그인 사용자 요약") UserSummary user) {

    @Schema(description = "로그인 사용자 요약")
    public record UserSummary(
            @Schema(description = "사용자 ID", example = "018f0c3e-2c77-7d72-a37e-2f5666f25d32")
                    UUID id,
            @Schema(description = "표시 이름", example = "집집이") String displayName) {}
}
