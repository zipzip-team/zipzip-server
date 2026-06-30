package org.zipzip.zipzipserver.global.exception;

import lombok.Getter;
import org.zipzip.zipzipserver.global.code.ErrorCode;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
