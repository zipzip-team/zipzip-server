package org.zipzip.zipzipserver.global.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(description = "요청 값 검증 실패 공통 응답")
public record OpenApiValidationErrorResponse(
        @Schema(description = "HTTP 상태 코드", example = "400") int status,
        @Schema(description = "오류 코드", example = "INVALID_REQUEST") String code,
        @Schema(description = "오류 메시지", example = "잘못된 요청입니다.") String message,
        @Schema(description = "필드별 검증 오류 목록") Map<String, List<String>> data) {}
