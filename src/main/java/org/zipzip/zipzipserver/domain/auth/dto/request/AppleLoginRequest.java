package org.zipzip.zipzipserver.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppleLoginRequest(
        @NotBlank String identityToken,
        @NotBlank String authorizationCode,
        @NotBlank String nonce,
        @Size(max = 50) String displayName) {}
