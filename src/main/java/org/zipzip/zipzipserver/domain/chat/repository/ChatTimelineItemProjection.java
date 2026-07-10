package org.zipzip.zipzipserver.domain.chat.repository;

import java.time.Instant;
import java.util.UUID;

public interface ChatTimelineItemProjection {

    UUID getId();

    String getTimelineType();

    UUID getPhotoId();

    String getContent();

    UUID getAuthorId();

    String getAuthorDisplayName();

    Instant getCreatedAt();

    Instant getUpdatedAt();
}
