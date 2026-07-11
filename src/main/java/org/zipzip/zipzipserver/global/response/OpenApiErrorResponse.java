package org.zipzip.zipzipserver.global.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "data가 없는 일반 오류 공통 응답")
public record OpenApiErrorResponse(
        @Schema(description = "HTTP 상태 코드", example = "401") int status,
        @Schema(description = "오류 코드", example = "UNAUTHORIZED") String code,
        @Schema(description = "오류 메시지", example = "인증이 필요합니다.") String message,
        @Schema(description = "오류 응답에서는 null", nullable = true) Void data) {}
