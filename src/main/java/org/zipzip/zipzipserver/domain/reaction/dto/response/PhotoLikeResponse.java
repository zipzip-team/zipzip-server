package org.zipzip.zipzipserver.domain.reaction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "사진 좋아요 상태 응답")
public record PhotoLikeResponse(
        @Schema(description = "사진 식별자") UUID photoId,
        @Schema(description = "요청 사용자의 좋아요 여부") boolean isLikedByMe,
        @Schema(description = "현재 좋아요 수", example = "4") long likeCount) {}
