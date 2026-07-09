package org.zipzip.zipzipserver.domain.sharedgroup.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.zipzip.zipzipserver.global.code.SuccessCode;

@Getter
@RequiredArgsConstructor
public enum SharedGroupSuccessCode implements SuccessCode {
    SHARED_GROUP_LIST_FOUND(HttpStatus.OK, "SHARED_GROUP_LIST_FOUND", "내 공유 그룹 목록을 조회했습니다."),
    SHARED_GROUP_CREATED(HttpStatus.CREATED, "SHARED_GROUP_CREATED", "공유 그룹을 생성했습니다."),
    SHARED_GROUP_FOUND(HttpStatus.OK, "SHARED_GROUP_FOUND", "공유 그룹을 조회했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
