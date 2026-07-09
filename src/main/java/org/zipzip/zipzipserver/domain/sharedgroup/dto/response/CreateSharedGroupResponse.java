package org.zipzip.zipzipserver.domain.sharedgroup.dto.response;

import java.time.Instant;
import java.util.UUID;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;

public record CreateSharedGroupResponse(
        UUID id,
        String name,
        String inviteCode,
        SharedGroupRole myRole,
        SharedGroupUserSummaryResponse createdBy,
        Instant createdAt) {}
