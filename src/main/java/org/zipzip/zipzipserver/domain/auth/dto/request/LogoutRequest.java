package org.zipzip.zipzipserver.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @Schema(description = "현재 세션의 Refresh Token", example = "eyJhbGciOi...") @NotBlank
                String refreshToken) {}
