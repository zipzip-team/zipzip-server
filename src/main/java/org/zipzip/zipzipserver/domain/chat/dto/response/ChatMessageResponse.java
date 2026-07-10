package org.zipzip.zipzipserver.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.zipzip.zipzipserver.domain.chat.entity.SharedGroupChatMessage;

@Schema(description = "일반 그룹 채팅 메시지 응답")
public record ChatMessageResponse(
        @Schema(description = "메시지 식별자", example = "22b50c69-ce29-4715-9825-dd27f4868bea") UUID id,
        @Schema(description = "메시지 본문", example = "이번 여행 사진 올려줘!") String content,
        @Schema(description = "작성자") ChatAuthorResponse author,
        @Schema(description = "요청 사용자가 작성자인지 여부", example = "true") boolean isAuthor,
        @Schema(description = "작성 시각(UTC ISO-8601)", example = "2026-07-03T10:15:30Z")
                Instant createdAt,
        @Schema(description = "수정 시각(UTC ISO-8601)", example = "2026-07-03T10:15:30Z")
                Instant updatedAt) {

    public static ChatMessageResponse from(SharedGroupChatMessage message, UUID requestAppUserId) {
        return new ChatMessageResponse(
                message.getId(),
                message.getContent(),
                ChatAuthorResponse.from(message.getAppUser()),
                message.getAppUser().getId().equals(requestAppUserId),
                message.getCreatedAt(),
                message.getUpdatedAt());
    }
}
