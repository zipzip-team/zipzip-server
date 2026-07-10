package org.zipzip.zipzipserver.domain.sharedgroup.repository;

import java.time.Instant;
import java.util.UUID;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;

public record SharedGroupMemberRow(
        UUID membershipId,
        UUID userId,
        String displayName,
        SharedGroupRole role,
        Instant joinedAt) {}
