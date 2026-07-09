package org.zipzip.zipzipserver.global.exception;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.zipzip.zipzipserver.global.code.ErrorCode;
import org.zipzip.zipzipserver.global.code.GlobalErrorCode;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // @RequestBody DTO의 @Valid 검증 실패를 필드별 에러 메시지로 변환합니다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Map<String, List<String>>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e) {
        Map<String, List<String>> fieldErrors = new HashMap<>();

        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            String field = fieldError.getField();
            String message =
                    Optional.ofNullable(fieldError.getDefaultMessage()).orElse("잘못된 값입니다.");

            fieldErrors.computeIfAbsent(field, key -> new ArrayList<>()).add(message);
        }

        log.warn("[MethodArgumentNotValidException] errors={}", fieldErrors);

        return ResponseEntity.status(GlobalErrorCode.INVALID_REQUEST.getHttpStatus())
                .body(BaseResponse.failure(GlobalErrorCode.INVALID_REQUEST, fieldErrors));
    }

    // JSON 문법 오류 또는 @RequestBody 타입 변환 실패를 공통 잘못된 요청 응답으로 변환합니다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e) {
        log.warn("[HttpMessageNotReadableException] message={}", e.getMessage());

        return ResponseEntity.status(GlobalErrorCode.INVALID_REQUEST.getHttpStatus())
                .body(BaseResponse.failure(GlobalErrorCode.INVALID_REQUEST));
    }

    // 필수 요청 헤더가 누락된 경우 공통 잘못된 요청 응답으로 변환합니다.
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<BaseResponse<Void>> handleMissingRequestHeader(
            MissingRequestHeaderException e) {
        log.warn("[MissingRequestHeaderException] header={}", e.getHeaderName());

        return ResponseEntity.status(GlobalErrorCode.INVALID_REQUEST.getHttpStatus())
                .body(BaseResponse.failure(GlobalErrorCode.INVALID_REQUEST));
    }

    // 서비스 계층에서 의도적으로 던진 비즈니스 예외를 해당 ErrorCode 응답으로 변환합니다.
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();

        log.warn("[BusinessException] code={}, message={}", errorCode.getCode(), e.getMessage());

        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(BaseResponse.failure(errorCode));
    }

    // 위에서 처리하지 못한 예상 밖의 예외를 서버 내부 오류 응답으로 변환합니다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleException(Exception e) {
        log.error("[UnhandledException] message={}", e.getMessage(), e);

        return ResponseEntity.status(GlobalErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(BaseResponse.failure(GlobalErrorCode.INTERNAL_SERVER_ERROR));
    }
}
