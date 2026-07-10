package org.zipzip.zipzipserver.domain.photo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "사진 업로드 완료 등록 응답")
public record PhotoUploadCompleteResponse(
        @Schema(description = "생성된 사진 목록") List<PhotoUploadItemResponse> items) {}
