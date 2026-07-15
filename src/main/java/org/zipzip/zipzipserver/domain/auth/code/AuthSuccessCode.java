package org.zipzip.zipzipserver.domain.auth.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.zipzip.zipzipserver.global.code.SuccessCode;

@Getter
@RequiredArgsConstructor
public enum AuthSuccessCode implements SuccessCode {
    AUTH_LOGIN_SUCCESS(HttpStatus.OK, "AUTH_LOGIN_SUCCESS", "로그인에 성공했습니다."),
    AUTH_DEVELOPMENT_TOKEN_ISSUED(
            HttpStatus.OK, "AUTH_DEVELOPMENT_TOKEN_ISSUED", "개발용 토큰을 발급했습니다."),
    AUTH_DEVELOPMENT_USER_DELETED(
            HttpStatus.OK, "AUTH_DEVELOPMENT_USER_DELETED", "개발용 사용자를 삭제했습니다."),
    AUTH_LOGOUT_SUCCESS(HttpStatus.OK, "AUTH_LOGOUT_SUCCESS", "로그아웃했습니다."),
    AUTH_TOKEN_REFRESHED(HttpStatus.OK, "AUTH_TOKEN_REFRESHED", "토큰을 갱신했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
