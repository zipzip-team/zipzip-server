package org.zipzip.zipzipserver.domain.storage;

import java.time.Instant;

public record PresignedUpload(String uploadUrl, Instant expiresAt) {}
