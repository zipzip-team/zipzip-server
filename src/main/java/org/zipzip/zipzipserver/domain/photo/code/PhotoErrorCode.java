package org.zipzip.zipzipserver.domain.photo.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.zipzip.zipzipserver.global.code.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum PhotoErrorCode implements ErrorCode {
    SHARED_ALBUM_NOT_FOUND(
            HttpStatus.NOT_FOUND, "SHARED_ALBUM_NOT_FOUND", "공유집(앨범) 또는 활성 멤버십이 없습니다."),
    PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND", "사진 또는 상위 활성 리소스·멤버십이 없습니다."),
    INVALID_UPLOAD_METADATA(
            HttpStatus.BAD_REQUEST, "INVALID_UPLOAD_METADATA", "업로드 요청 형식이 올바르지 않습니다."),
    TOO_MANY_FILES(HttpStatus.BAD_REQUEST, "TOO_MANY_FILES", "한 요청에 20개를 초과하는 파일을 보낼 수 없습니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "파일 크기가 20MiB를 초과합니다."),
    UNSUPPORTED_IMAGE_TYPE(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_TYPE", "지원하지 않는 이미지 형식입니다."),
    UPLOAD_OBJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "UPLOAD_OBJECT_NOT_FOUND", "업로드 예약을 찾을 수 없습니다."),
    UPLOAD_NOT_COMPLETED(
            HttpStatus.CONFLICT, "UPLOAD_NOT_COMPLETED", "아직 원본이 Object Storage에 업로드되지 않았습니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "cursor가 유효하지 않습니다."),
    NOT_PHOTO_UPLOADER(HttpStatus.FORBIDDEN, "NOT_PHOTO_UPLOADER", "사진 업로더만 수행할 수 있습니다."),
    INVALID_TAKEN_AT(HttpStatus.BAD_REQUEST, "INVALID_TAKEN_AT", "UTC ISO-8601 형식이 아닙니다."),
    INVALID_PHOTO_LOCATION(
            HttpStatus.BAD_REQUEST, "INVALID_PHOTO_LOCATION", "위치 필드 세 값 중 일부만 전달했습니다."),
    INVALID_PHOTO_IDS(HttpStatus.BAD_REQUEST, "INVALID_PHOTO_IDS", "사진 식별자 목록이 올바르지 않습니다."),
    PHOTO_NOT_IN_SAME_SHARED_GROUP(
            HttpStatus.CONFLICT,
            "PHOTO_NOT_IN_SAME_SHARED_GROUP",
            "공유집(앨범)과 다른 공유 그룹에 속한 사진이 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
