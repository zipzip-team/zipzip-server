package org.zipzip.zipzipserver.domain.photo.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.zipzip.zipzipserver.global.code.SuccessCode;

@Getter
@RequiredArgsConstructor
public enum PhotoSuccessCode implements SuccessCode {
    PHOTO_UPLOAD_URLS_ISSUED(HttpStatus.OK, "PHOTO_UPLOAD_URLS_ISSUED", "사진 업로드 URL을 발급했습니다."),
    PHOTOS_CREATED(HttpStatus.CREATED, "PHOTOS_CREATED", "사진을 업로드했습니다."),
    PHOTO_LIST_FOUND(HttpStatus.OK, "PHOTO_LIST_FOUND", "사진 목록을 조회했습니다."),
    PHOTO_UPDATED(HttpStatus.OK, "PHOTO_UPDATED", "사진 메타데이터를 수정했습니다."),
    PHOTO_DELETED(HttpStatus.OK, "PHOTO_DELETED", "사진을 삭제했습니다."),
    PHOTOS_DELETED(HttpStatus.OK, "PHOTOS_DELETED", "사진을 일괄 삭제했습니다."),
    PHOTOS_ATTACHED(HttpStatus.OK, "PHOTOS_ATTACHED", "공유집(앨범)에 사진을 추가했습니다."),
    PHOTOS_DETACHED(HttpStatus.OK, "PHOTOS_DETACHED", "공유집(앨범)에서 사진을 제거했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
