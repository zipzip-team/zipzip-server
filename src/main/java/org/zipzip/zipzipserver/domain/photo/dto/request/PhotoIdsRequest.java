package org.zipzip.zipzipserver.domain.photo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "사진 식별자 목록 요청(중복 없이 1~100개)")
public record PhotoIdsRequest(
        @Schema(
                        description = "대상 사진 식별자 목록. 중복 없이 1~100개를 전달하며, 요청 경로의 작업 대상이 됩니다.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                List<UUID> photoIds) {}
