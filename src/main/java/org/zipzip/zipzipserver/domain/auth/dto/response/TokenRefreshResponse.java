package org.zipzip.zipzipserver.domain.auth.dto.response;

public record TokenRefreshResponse(
        String accessToken, String refreshToken, String tokenType, long expiresIn) {}
