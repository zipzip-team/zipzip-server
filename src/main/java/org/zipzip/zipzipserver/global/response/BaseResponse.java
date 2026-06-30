package org.zipzip.zipzipserver.global.response;

import org.zipzip.zipzipserver.global.code.ErrorCode;
import org.zipzip.zipzipserver.global.code.SuccessCode;

public record BaseResponse<T>(int status, String code, String message, T data) {
    public static <T> BaseResponse<T> success(SuccessCode successCode, T data) {
        return new BaseResponse<>(
                successCode.getHttpStatus().value(),
                successCode.getCode(),
                successCode.getMessage(),
                data);
    }

    public static BaseResponse<Void> success(SuccessCode successCode) {
        return new BaseResponse<>(
                successCode.getHttpStatus().value(),
                successCode.getCode(),
                successCode.getMessage(),
                null);
    }

    public static <T> BaseResponse<T> failure(ErrorCode errorCode, T data) {
        return new BaseResponse<>(
                errorCode.getHttpStatus().value(),
                errorCode.getCode(),
                errorCode.getMessage(),
                data);
    }

    public static BaseResponse<Void> failure(ErrorCode errorCode) {
        return new BaseResponse<>(
                errorCode.getHttpStatus().value(),
                errorCode.getCode(),
                errorCode.getMessage(),
                null);
    }
}
