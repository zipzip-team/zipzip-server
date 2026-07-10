package org.zipzip.zipzipserver.domain.sharedgroup.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공유 그룹 참여 공통 응답")
public record SharedGroupJoinResponseEnvelope(
        @Schema(example = "201") int status,
        @Schema(example = "SHARED_GROUP_JOINED") String code,
        @Schema(example = "공유 그룹에 참여했습니다.") String message,
        SharedGroupJoinResponse data) {}
