package org.zipzip.zipzipserver.domain.album.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공유집(앨범) 일괄 삭제 응답")
public record SharedAlbumBulkDeleteResponse(
        @Schema(description = "soft delete한 공유집(앨범) 수", example = "2") int deletedAlbumCount,
        @Schema(description = "마지막 소속을 잃어 함께 soft delete된 사진 수", example = "3")
                int deletedPhotoCount) {}
