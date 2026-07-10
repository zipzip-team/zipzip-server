package org.zipzip.zipzipserver.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "그룹 채팅 메시지 작성 요청")
public record CreateChatMessageRequest(
        @Schema(
                        description = "작성할 일반 채팅 메시지. 앞뒤 공백을 제거한 뒤 1~1,000자여야 합니다.",
                        example = "이번 여행 사진 올려줘!",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        minLength = 1,
                        maxLength = 1000)
                String content) {}
