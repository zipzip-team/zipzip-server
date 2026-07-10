package org.zipzip.zipzipserver.domain.sharedgroup.dto.response;

import java.time.Instant;
import java.util.UUID;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;

public record SharedGroupJoinResponse(
        UUID sharedGroupId, String name, SharedGroupRole myRole, Instant joinedAt) {}
