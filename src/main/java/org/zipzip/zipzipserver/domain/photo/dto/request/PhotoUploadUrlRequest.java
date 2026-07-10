package org.zipzip.zipzipserver.domain.photo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "사진 업로드 URL 발급 요청")
public record PhotoUploadUrlRequest(
        @Schema(description = "발급할 업로드 URL 목록(1~20개)") List<UploadUrlFileSpec> files) {

    @Schema(description = "업로드할 파일 사양")
    public record UploadUrlFileSpec(
            @Schema(description = "업로드할 이미지의 MIME type", example = "image/jpeg") String contentType,
            @Schema(description = "업로드할 파일 크기(byte, 1~20MiB)", example = "2849182")
                    Long sizeBytes) {}
}
