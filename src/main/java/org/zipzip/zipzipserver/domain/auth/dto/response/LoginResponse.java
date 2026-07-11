package org.zipzip.zipzipserver.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Apple 로그인 응답")
public record LoginResponse(
        @Schema(
                        description = "인증이 필요한 API의 Authorization 헤더에 Bearer 접두사와 함께 넣을 Access Token",
                        format = "JWT",
                        example = "eyJhbGciOiJIUzI1NiJ9...")
                String accessToken,
        @Schema(
                        description = "다음 /auth/refresh 요청 본문에 넣을 Refresh Token. 안전한 저장소에 보관합니다.",
                        format = "JWT",
                        example = "eyJhbGciOiJIUzI1NiJ9...")
                String refreshToken,
        @Schema(description = "Access Token을 Authorization 헤더에 붙일 인증 방식", example = "Bearer")
                String tokenType,
        @Schema(description = "Access Token의 남은 유효 시간(초)", example = "1800", minimum = "1")
                long expiresIn,
        @Schema(description = "이번 요청으로 새 사용자가 생성되었는지 여부", example = "true")
                boolean isNewUser,
        @Schema(description = "탈퇴 상태였던 사용자가 이번 요청으로 복구되었는지 여부", example = "false")
                boolean isRestoredUser,
        @Schema(description = "로그인 사용자 요약") UserSummary user) {

    @Schema(description = "로그인 사용자 요약")
    public record UserSummary(
            @Schema(description = "사용자 ID", example = "018f0c3e-2c77-7d72-a37e-2f5666f25d32")
                    UUID id,
            @Schema(description = "표시 이름", example = "집집이") String displayName) {}
}
