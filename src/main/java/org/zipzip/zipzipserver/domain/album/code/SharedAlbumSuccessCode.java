package org.zipzip.zipzipserver.domain.album.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.zipzip.zipzipserver.global.code.SuccessCode;

@Getter
@RequiredArgsConstructor
public enum SharedAlbumSuccessCode implements SuccessCode {
    SHARED_ALBUM_LIST_FOUND(HttpStatus.OK, "SHARED_ALBUM_LIST_FOUND", "공유집(앨범) 목록을 조회했습니다."),
    SHARED_ALBUM_CREATED(HttpStatus.CREATED, "SHARED_ALBUM_CREATED", "공유집(앨범)을 생성했습니다."),
    SHARED_ALBUM_FOUND(HttpStatus.OK, "SHARED_ALBUM_FOUND", "공유집(앨범)을 조회했습니다."),
    SHARED_ALBUM_UPDATED(HttpStatus.OK, "SHARED_ALBUM_UPDATED", "공유집(앨범) 이름을 수정했습니다."),
    SHARED_ALBUM_DELETED(HttpStatus.OK, "SHARED_ALBUM_DELETED", "공유집(앨범)을 삭제했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
