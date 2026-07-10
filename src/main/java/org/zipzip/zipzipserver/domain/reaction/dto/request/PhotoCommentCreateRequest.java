package org.zipzip.zipzipserver.domain.reaction.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사진 댓글 작성 요청")
public record PhotoCommentCreateRequest(
        @Schema(
                        description = "댓글 본문. trim 후 1~1,000자",
                        example = "사진 너무 좋다!",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String content) {}
