package org.zipzip.zipzipserver.domain.sharedgroup.dto.response;

import java.time.Instant;
import java.util.UUID;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;

public record SharedGroupUpdateResponse(UUID id, String name, Instant updatedAt) {

    public static SharedGroupUpdateResponse from(SharedGroup sharedGroup) {
        return new SharedGroupUpdateResponse(
                sharedGroup.getId(), sharedGroup.getName(), sharedGroup.getUpdatedAt());
    }
}
