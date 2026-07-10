package org.zipzip.zipzipserver.domain.storage;

import java.time.Instant;

public record PresignedDownload(String url, Instant expiresAt) {}
