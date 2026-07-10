package org.zipzip.zipzipserver.domain.photo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공유집(앨범) 사진 일괄 삭제 응답")
public record PhotoBulkDeleteResponse(
        @Schema(description = "soft delete한 사진 수", example = "2") int deletedCount) {}
