package org.zipzip.zipzipserver.domain.sharedgroup.dto.response;

import java.util.UUID;

public record SharedGroupUserSummaryResponse(UUID userId, String displayName) {}
