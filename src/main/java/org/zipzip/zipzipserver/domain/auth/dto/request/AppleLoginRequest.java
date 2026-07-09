package org.zipzip.zipzipserver.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Apple 로그인 요청")
public record AppleLoginRequest(
        @Schema(
                        description = "Apple이 발급한 identity token",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String identityToken,
        @Schema(
                        description = "Apple authorization code",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String authorizationCode,
        @Schema(description = "Apple 로그인 요청 nonce", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String nonce,
        @Schema(description = "최초 가입 또는 탈퇴 사용자 복구 시 사용할 표시 이름", example = "집집이") @Size(max = 50)
                String displayName) {}
