package org.zipzip.zipzipserver.domain.reaction.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.zipzip.zipzipserver.global.code.SuccessCode;

@Getter
@RequiredArgsConstructor
public enum ReactionSuccessCode implements SuccessCode {
    PHOTO_FOUND(HttpStatus.OK, "PHOTO_FOUND", "사진을 조회했습니다."),
    PHOTO_LIKED(HttpStatus.OK, "PHOTO_LIKED", "사진에 좋아요를 설정했습니다."),
    PHOTO_UNLIKED(HttpStatus.OK, "PHOTO_UNLIKED", "사진 좋아요를 취소했습니다."),
    PHOTO_COMMENT_LIST_FOUND(HttpStatus.OK, "PHOTO_COMMENT_LIST_FOUND", "사진 댓글 목록을 조회했습니다."),
    PHOTO_COMMENT_CREATED(HttpStatus.CREATED, "PHOTO_COMMENT_CREATED", "사진 댓글을 작성했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
