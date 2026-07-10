package org.zipzip.zipzipserver.domain.photo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공유집(앨범)에 기존 사진 추가 응답")
public record PhotoAttachResponse(
        @Schema(description = "새로 추가한 매핑 수", example = "1") int attachedCount,
        @Schema(description = "이미 해당 공유집(앨범)에 속해 있어 건너뛴 수", example = "1")
                int alreadyAttachedCount) {}
