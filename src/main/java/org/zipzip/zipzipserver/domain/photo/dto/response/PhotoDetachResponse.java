package org.zipzip.zipzipserver.domain.photo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공유집(앨범)에서 사진 제거 응답")
public record PhotoDetachResponse(
        @Schema(description = "물리 삭제한 shared_album_photo 매핑 수", example = "1") int detachedCount,
        @Schema(description = "마지막 소속을 잃어 함께 soft delete된 사진 수", example = "0")
                int deletedPhotoCount) {}
