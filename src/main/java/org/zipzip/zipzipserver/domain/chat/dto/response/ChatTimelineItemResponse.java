package org.zipzip.zipzipserver.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "그룹 채팅 타임라인 항목")
public record ChatTimelineItemResponse(
        @Schema(description = "항목 유형", example = "PHOTO_COMMENT") ChatTimelineType type,
        @Schema(description = "메시지 또는 댓글 식별자", example = "22b50c69-ce29-4715-9825-dd27f4868bea")
                UUID id,
        @Schema(
                        description = "사진 댓글인 경우 연결된 사진 식별자. 일반 메시지면 null입니다.",
                        example = "385ff765-b20c-49a2-8e62-e1457784aa15",
                        nullable = true)
                UUID photoId,
        @Schema(description = "메시지 또는 댓글 본문", example = "사진 너무 좋다!") String content,
        @Schema(description = "작성자") ChatAuthorResponse author,
        @Schema(description = "요청 사용자가 작성자인지 여부", example = "true") boolean isAuthor,
        @Schema(description = "작성 시각(UTC ISO-8601)", example = "2026-07-03T10:15:30Z")
                Instant createdAt,
        @Schema(description = "수정 시각(UTC ISO-8601)", example = "2026-07-03T10:15:30Z")
                Instant updatedAt) {}
