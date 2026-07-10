package org.zipzip.zipzipserver.domain.chat.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.zipzip.zipzipserver.global.code.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements ErrorCode {
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "cursor가 올바르지 않습니다."),
    INVALID_CHAT_MESSAGE_CONTENT(
            HttpStatus.BAD_REQUEST, "INVALID_CHAT_MESSAGE_CONTENT", "채팅 메시지 내용이 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
