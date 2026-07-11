package org.zipzip.zipzipserver.domain.photo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "사진 업로드 URL 발급 요청")
public record PhotoUploadUrlRequest(
        @Schema(description = "발급할 업로드 URL 목록. 요청당 1~20개", requiredMode = Schema.RequiredMode.REQUIRED)
                List<UploadUrlFileSpec> files) {

    @Schema(description = "업로드할 파일 사양")
    public record UploadUrlFileSpec(
            @Schema(description = "업로드할 이미지 MIME type. image/* 형식만 허용", example = "image/jpeg", requiredMode = Schema.RequiredMode.REQUIRED) String contentType,
            @Schema(description = "업로드할 파일 크기(byte). 1~20MiB이며 presigned PUT 서명 조건에도 포함", example = "2849182", minimum = "1", maximum = "20971520", requiredMode = Schema.RequiredMode.REQUIRED)
                    Long sizeBytes) {}
}
