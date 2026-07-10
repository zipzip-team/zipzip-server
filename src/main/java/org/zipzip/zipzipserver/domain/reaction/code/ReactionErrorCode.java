package org.zipzip.zipzipserver.domain.reaction.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.zipzip.zipzipserver.global.code.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum ReactionErrorCode implements ErrorCode {
    INVALID_PHOTO_COMMENT_CONTENT(
            HttpStatus.BAD_REQUEST,
            "INVALID_PHOTO_COMMENT_CONTENT",
            "사진 댓글 내용은 공백이 아니며 1,000자 이하여야 합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
