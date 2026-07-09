package org.zipzip.zipzipserver.domain.sharedgroup.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SharedGroupJoinRequest(@NotBlank String inviteCode) {}
