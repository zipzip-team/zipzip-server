package org.zipzip.zipzipserver.domain.auth.dto.response;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        boolean isNewUser,
        boolean isRestoredUser,
        UserSummary user) {

    public record UserSummary(UUID id, String displayName) {}
}
