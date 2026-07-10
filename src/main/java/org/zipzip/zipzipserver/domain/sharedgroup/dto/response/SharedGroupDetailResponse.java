package org.zipzip.zipzipserver.domain.sharedgroup.dto.response;

import java.time.Instant;
import java.util.UUID;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;

public record SharedGroupDetailResponse(
        UUID id,
        String name,
        SharedGroupRole myRole,
        SharedGroupUserSummaryResponse createdBy,
        long memberCount,
        long sharedAlbumCount,
        long photoCount,
        Instant createdAt,
        Instant updatedAt) {}
