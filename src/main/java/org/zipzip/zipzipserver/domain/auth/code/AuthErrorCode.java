package org.zipzip.zipzipserver.domain.auth.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.zipzip.zipzipserver.global.code.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
    DISPLAY_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "DISPLAY_NAME_REQUIRED", "표시 이름이 필요합니다."),
    INVALID_DISPLAY_NAME(HttpStatus.BAD_REQUEST, "INVALID_DISPLAY_NAME", "표시 이름이 올바르지 않습니다."),
    INVALID_APPLE_TOKEN(
            HttpStatus.UNAUTHORIZED, "INVALID_APPLE_TOKEN", "Apple identity token이 올바르지 않습니다."),
    INVALID_APPLE_AUTHORIZATION_CODE(
            HttpStatus.UNAUTHORIZED,
            "INVALID_APPLE_AUTHORIZATION_CODE",
            "Apple authorization code가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(
            HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh Token이 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
