package org.zipzip.zipzipserver.domain.sharedgroup.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;

public record SharedGroupMemberListResponse(
        List<Member> items, String nextCursor, boolean hasNext) {

    public record Member(
            UUID userId,
            String displayName,
            SharedGroupRole role,
            boolean isMe,
            Instant joinedAt) {}
}
