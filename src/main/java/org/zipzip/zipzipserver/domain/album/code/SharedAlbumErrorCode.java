package org.zipzip.zipzipserver.domain.album.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.zipzip.zipzipserver.global.code.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum SharedAlbumErrorCode implements ErrorCode {
    SHARED_GROUP_NOT_FOUND(
            HttpStatus.NOT_FOUND, "SHARED_GROUP_NOT_FOUND", "공유 그룹 또는 활성 멤버십이 없습니다."),
    SHARED_ALBUM_NOT_FOUND(
            HttpStatus.NOT_FOUND, "SHARED_ALBUM_NOT_FOUND", "공유집(앨범) 또는 활성 멤버십이 없습니다."),
    INVALID_SHARED_ALBUM_NAME(
            HttpStatus.BAD_REQUEST, "INVALID_SHARED_ALBUM_NAME", "이름이 공백이거나 100자를 초과합니다."),
    INVALID_SHARED_ALBUM_IDS(
            HttpStatus.BAD_REQUEST,
            "INVALID_SHARED_ALBUM_IDS",
            "요청 본문이 null이거나 공유집(앨범) 식별자 목록이 비었거나, null 원소를 포함하거나, 중복이 있거나, 100개를" + " 초과합니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "cursor가 유효하지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
