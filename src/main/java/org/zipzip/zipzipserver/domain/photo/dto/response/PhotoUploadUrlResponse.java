package org.zipzip.zipzipserver.domain.photo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "사진 업로드 URL 발급 응답")
public record PhotoUploadUrlResponse(
        @Schema(description = "발급된 업로드 URL 목록") List<UploadUrlItem> uploads) {

    @Schema(description = "발급된 업로드 URL 항목")
    public record UploadUrlItem(
            @Schema(description = "업로드 대상 객체 키") String objectKey,
            @Schema(description = "presigned PUT URL") String uploadUrl,
            @Schema(description = "업로드 URL 만료 시각") Instant uploadUrlExpiresAt) {}
}
