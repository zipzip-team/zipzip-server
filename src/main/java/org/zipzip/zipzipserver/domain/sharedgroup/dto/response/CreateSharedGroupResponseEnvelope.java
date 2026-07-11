package org.zipzip.zipzipserver.domain.sharedgroup.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공유 그룹 생성 공통 응답")
public record CreateSharedGroupResponseEnvelope(
        @Schema(example = "201") int status,
        @Schema(example = "SHARED_GROUP_CREATED") String code,
        @Schema(example = "공유 그룹을 생성했습니다.") String message,
        @Schema(description = "생성된 공유 그룹 정보") CreateSharedGroupResponse data) {}
