package org.zipzip.zipzipserver.domain.chat.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.zipzip.zipzipserver.global.code.SuccessCode;

@Getter
@RequiredArgsConstructor
public enum ChatSuccessCode implements SuccessCode {
    SHARED_GROUP_CHAT_TIMELINE_FOUND(
            HttpStatus.OK, "SHARED_GROUP_CHAT_TIMELINE_FOUND", "그룹 채팅 타임라인을 조회했습니다."),
    SHARED_GROUP_CHAT_MESSAGE_CREATED(
            HttpStatus.CREATED, "SHARED_GROUP_CHAT_MESSAGE_CREATED", "그룹 채팅 메시지를 작성했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
