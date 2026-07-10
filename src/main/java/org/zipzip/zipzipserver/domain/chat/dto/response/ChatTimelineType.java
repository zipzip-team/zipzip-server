package org.zipzip.zipzipserver.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "채팅 타임라인 항목 유형",
        allowableValues = {"CHAT_MESSAGE", "PHOTO_COMMENT"})
public enum ChatTimelineType {
    CHAT_MESSAGE(0),
    PHOTO_COMMENT(1);

    private final int order;

    ChatTimelineType(int order) {
        this.order = order;
    }

    public int getOrder() {
        return order;
    }
}
