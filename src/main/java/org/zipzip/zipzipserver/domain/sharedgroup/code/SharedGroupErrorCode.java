package org.zipzip.zipzipserver.domain.sharedgroup.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.zipzip.zipzipserver.global.code.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum SharedGroupErrorCode implements ErrorCode {
    INVALID_SHARED_GROUP_NAME(
            HttpStatus.BAD_REQUEST, "INVALID_SHARED_GROUP_NAME", "공유 그룹 이름이 올바르지 않습니다."),
    SHARED_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "SHARED_GROUP_NOT_FOUND", "공유 그룹을 찾을 수 없습니다."),
    INVITE_CODE_GENERATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, "INVITE_CODE_GENERATION_FAILED", "초대 코드 생성에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
