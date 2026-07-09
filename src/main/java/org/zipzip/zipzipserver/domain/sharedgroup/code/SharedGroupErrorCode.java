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
    HOST_CANNOT_LEAVE_SHARED_GROUP(
            HttpStatus.FORBIDDEN, "HOST_CANNOT_LEAVE_SHARED_GROUP", "방장은 공유 그룹에서 나갈 수 없습니다."),
    INVALID_INVITE_CODE(HttpStatus.BAD_REQUEST, "INVALID_INVITE_CODE", "초대 코드가 올바르지 않습니다."),
    ALREADY_JOINED_SHARED_GROUP(
            HttpStatus.CONFLICT, "ALREADY_JOINED_SHARED_GROUP", "이미 참여한 공유 그룹입니다."),
    INVITE_CODE_GENERATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, "INVITE_CODE_GENERATION_FAILED", "초대 코드 생성에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
