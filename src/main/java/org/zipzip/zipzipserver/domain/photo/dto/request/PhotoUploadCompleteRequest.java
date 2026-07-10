package org.zipzip.zipzipserver.domain.photo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "사진 업로드 완료 등록 요청")
public record PhotoUploadCompleteRequest(
        @Schema(description = "등록할 파일 목록(중복 없이 1~20개)") List<CompleteFileSpec> files) {

    @Schema(description = "등록할 파일 사양")
    public record CompleteFileSpec(
            @Schema(description = "PHOTO-02에서 발급받은 객체 키") String objectKey,
            @Schema(description = "EXIF에서 추출한 촬영 기기명(100자 이하)", example = "iPhone 15")
                    String deviceModel,
            @Schema(description = "EXIF 촬영일시(UTC ISO-8601)", example = "2026-06-30T04:20:00Z")
                    String takenAt,
            @Schema(description = "촬영 위치 위도") Double latitude,
            @Schema(description = "촬영 위치 경도") Double longitude,
            @Schema(description = "촬영 위치명") String locationName,
            @Schema(description = "위치정보 추론 여부(기본값 false)") Boolean isInferred,
            @Schema(description = "이미지 가로 픽셀") Integer width,
            @Schema(description = "이미지 세로 픽셀") Integer height) {}
}
