package org.zipzip.zipzipserver.domain.sharedgroup.dto.response;

import java.time.Instant;
import java.util.UUID;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;

public record SharedGroupSummaryResponse(
        UUID id,
        String name,
        SharedGroupRole myRole,
        long memberCount,
        long sharedAlbumCount,
        long photoCount,
        Instant joinedAt,
        Instant updatedAt) {}
