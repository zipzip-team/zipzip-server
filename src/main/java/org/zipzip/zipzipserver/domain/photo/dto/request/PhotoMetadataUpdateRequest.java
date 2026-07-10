package org.zipzip.zipzipserver.domain.photo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PHOTO-04 요청 본문의 Swagger 문서화 전용 타입. 실제 바인딩은 컨트롤러에서 {@code Map<String, Object>}로 받는다 — "필드를 아예
 * 생략함(값 유지)"과 "명시적으로 null을 보냄(값 제거)"을 구분해야 하는데 이 타입으로 바인딩하면 그 구분이 불가능하기 때문이다. 그 구분을 지키면서도 Swagger에는
 * 실제 요청 형태가 보이도록, 이 타입은 문서 스키마로만 참조하고 바인딩에는 쓰지 않는다.
 */
@Schema(description = "사진 메타데이터 수정 요청. 필드를 생략하면 기존 값을 유지하고, null을 명시하면 해당 값을 제거한다.")
public record PhotoMetadataUpdateRequest(
        @Schema(
                        description = "UTC ISO-8601 촬영일시. null이면 제거, 생략하면 유지",
                        example = "2026-06-30T04:20:00Z")
                String takenAt,
        @Schema(description = "위도. longitude, locationName과 함께 셋 다 보내거나 셋 다 생략/null")
                Double latitude,
        @Schema(description = "경도. latitude, locationName과 함께 셋 다 보내거나 셋 다 생략/null")
                Double longitude,
        @Schema(description = "위치명. latitude, longitude와 함께 셋 다 보내거나 셋 다 생략/null")
                String locationName,
        @Schema(description = "위치정보 추론 여부. 위치 필드와 함께 보낼 때만 반영, 생략하면 false") Boolean isInferred) {}
