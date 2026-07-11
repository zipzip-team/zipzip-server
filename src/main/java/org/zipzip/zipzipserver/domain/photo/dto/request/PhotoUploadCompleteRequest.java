package org.zipzip.zipzipserver.domain.photo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "사진 업로드 완료 등록 요청")
public record PhotoUploadCompleteRequest(
        @Schema(description = "PHOTO-02 업로드를 완료해 등록할 파일 목록. objectKey 중복 없이 1~20개", requiredMode = Schema.RequiredMode.REQUIRED)
                List<CompleteFileSpec> files) {

    @Schema(description = "등록할 파일 사양")
    public record CompleteFileSpec(
            @Schema(description = "PHOTO-02에서 발급받고 해당 PUT URL로 업로드를 완료한 객체 키", requiredMode = Schema.RequiredMode.REQUIRED) String objectKey,
            @Schema(description = "EXIF에서 추출한 촬영 기기명. 없으면 생략 또는 null, 100자 이하", example = "iPhone 15", maxLength = 100, nullable = true)
                    String deviceModel,
            @Schema(description = "EXIF 촬영일시(UTC ISO-8601). 없으면 생략 또는 null", example = "2026-06-30T04:20:00Z", nullable = true)
                    String takenAt,
            @Schema(description = "촬영 위치 위도") Double latitude,
            @Schema(description = "촬영 위치 경도") Double longitude,
            @Schema(description = "촬영 위치명") String locationName,
            @Schema(description = "위치정보 추론 여부(기본값 false)") Boolean isInferred,
            @Schema(description = "이미지 가로 픽셀") Integer width,
            @Schema(description = "이미지 세로 픽셀") Integer height) {}
}
