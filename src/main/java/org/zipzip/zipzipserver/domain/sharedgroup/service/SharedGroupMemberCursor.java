package org.zipzip.zipzipserver.domain.sharedgroup.service;

import java.time.Instant;
import java.util.UUID;

record SharedGroupMemberCursor(Instant joinedAt, UUID membershipId) {}
