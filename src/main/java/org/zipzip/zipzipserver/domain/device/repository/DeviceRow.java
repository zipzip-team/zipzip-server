package org.zipzip.zipzipserver.domain.device.repository;

import java.util.UUID;

public record DeviceRow(UUID appUserId, UUID id, String name) {}
