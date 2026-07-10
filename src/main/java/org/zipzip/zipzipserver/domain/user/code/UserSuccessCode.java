package org.zipzip.zipzipserver.domain.user.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.zipzip.zipzipserver.global.code.SuccessCode;

@Getter
@RequiredArgsConstructor
public enum UserSuccessCode implements SuccessCode {
    USER_PROFILE_FOUND(HttpStatus.OK, "USER_PROFILE_FOUND", "내 프로필을 조회했습니다."),
    USER_PROFILE_UPDATED(HttpStatus.OK, "USER_PROFILE_UPDATED", "내 프로필을 수정했습니다."),
    USER_WITHDRAWN(HttpStatus.OK, "USER_WITHDRAWN", "사용자 탈퇴가 완료되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
