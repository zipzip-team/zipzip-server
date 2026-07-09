package org.zipzip.zipzipserver.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppleLoginRequest(
        @Schema(description = "Apple identity token", example = "eyJhbGciOi...") @NotBlank
                String identityToken,
        @Schema(description = "Apple authorization code", example = "c1a2b3...") @NotBlank
                String authorizationCode,
        @Schema(description = "Apple 로그인 요청 nonce", example = "nonce-value") @NotBlank String nonce,
        @Schema(description = "사용자 표시 이름. 최초 가입 또는 탈퇴 사용자 복구 시 필요", example = "집집이") @Size(max = 50)
                String displayName) {}
