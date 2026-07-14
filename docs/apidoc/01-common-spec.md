# Zipzip API 공통 규격

## 1. 기본 규칙

| 항목 | 규칙 |
|---|---|
| Base Path | `/api/v1` |
| 기본 요청 형식 | `application/json` |
| 사진 원본 업로드 | iOS가 presigned PUT URL로 Object Storage에 직접 업로드(서버 경유 없음) |
| 인증 | `Authorization: Bearer {accessToken}` |
| 식별자 | UUID 문자열 |
| 시간 | `java.time.Instant` 직렬화 기준의 UTC ISO-8601 문자열. 예: `2026-07-03T10:15:30Z` |
| JSON 필드명 | `camelCase` |
| 목록 페이지네이션 | 불투명 cursor 기반 |
| 삭제 응답 | `200 OK`와 `data: null` |

Apple 로그인과 토큰 갱신을 제외한 모든 API는 인증이 필요하다. 인증된 사용자라도 공유 그룹 내부 리소스에는 해당 공유 그룹의 활성 멤버십이 있어야 한다.

시간 필드는 서버 내부에서 `Instant`로 다루며 API에서는 UTC `Z` 접미사가 붙은 ISO-8601 문자열로 주고받는다.
클라이언트 화면 표시 시간대 변환은 앱에서 처리한다.
서버는 `LocalDateTime`처럼 시간대가 없는 지역 시각을 API 계약 타입으로 사용하지 않는다.

## 2. Request Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Authorization` | String | 조건부 | Bearer Access Token. 비인증 API에서는 생략한다. | `Bearer eyJhbGciOiJIUzI1NiJ9...` |
| `Idempotency-Key` | UUID String | 조건부 | 멱등성 적용 POST API의 요청 식별자 | `54cf8d7e-a23e-4e76-90f7-603f122b1507` |
| `Content-Type` | String | 필수 | 요청 본문 형식 | `application/json` |
| `Accept` | String | 권장 | 응답 형식 | `application/json` |

## 3. 멱등성

네트워크 재시도로 상태 변경이 중복되는 것을 막기 위해 다음 API는 `Idempotency-Key`를 필수로 받는다.

- AUTH-01 Apple 로그인
- AUTH-02 토큰 갱신
- GROUP-02 공유 그룹 생성
- INVITE-02 초대 코드로 참여
- ALBUM-02 앨범 생성
- ALBUM-06 공유집(앨범) 일괄 삭제
- PHOTO-03 사진 업로드 완료 등록
- PHOTO-07 공유집(앨범)에 기존 사진 추가
- PHOTO-08 공유집(앨범)에서 사진 제거
- COMMENT-02 사진 댓글 작성
- CHAT-02 그룹 채팅 메시지 작성

서버는 인증 사용자 또는 인증 전 요청 주체, HTTP Method, API Path, `Idempotency-Key`를 조합해 요청을 식별한다.

| 상황 | 처리 |
|---|---|
| 같은 key와 같은 요청 본문 | 최초 응답의 HTTP status와 body를 그대로 반환한다. |
| 같은 key와 다른 요청 본문 | `409 IDEMPOTENCY_KEY_REUSED`를 반환한다. |
| 같은 key의 최초 요청이 처리 중 | `409 IDEMPOTENCY_REQUEST_IN_PROGRESS`를 반환한다. |
| 보관 기간 경과 후 같은 key 재사용 | 새로운 요청으로 처리할 수 있다. |

일반 API의 멱등성 기록은 24시간, 토큰 원문을 포함할 수 있는 인증 API 응답 기록은 암호화하여 10분 동안 보관한다. 재처리 응답에는 `Idempotency-Replayed: true` 헤더를 포함한다.

## 4. Common Response Format

현재 서버의 `BaseResponse<T>` 구현과 동일한 형식을 사용한다.

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `status` | Integer | O | HTTP 상태 코드 |
| `code` | String | O | 성공 또는 오류 코드 |
| `message` | String | O | 성공 또는 오류 메시지 |
| `data` | Object, Array, null | O | 실제 응답 데이터. 데이터가 없으면 `null` |

### 성공 응답 예시

```json
{
  "status": 200,
  "code": "OK",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {}
}
```

### 생성 응답 예시

```json
{
  "status": 201,
  "code": "CREATED",
  "message": "요청이 성공적으로 생성되었습니다.",
  "data": {}
}
```

### 실패 응답 예시

```json
{
  "status": 400,
  "code": "INVALID_REQUEST",
  "message": "잘못된 요청입니다.",
  "data": {
    "displayName": ["공백일 수 없습니다."]
  }
}
```

## 5. 페이지네이션

| Query 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---:|---|---|
| `cursor` | String | X | null | 이전 응답의 `nextCursor`. 최초 조회에서는 생략한다. |
| `size` | Integer | X | 20 | 페이지 크기. 범위는 1~100이다. |

```json
{
  "items": [],
  "nextCursor": null,
  "hasNext": false
}
```

cursor는 서버 내부 정렬 키를 인코딩한 불투명 문자열이다. 클라이언트가 cursor의 내용을 해석하거나 생성하지 않는다.

## 6. 공통 오류 코드

| HTTP Status | code | message | 사용 조건 |
|---:|---|---|---|
| 400 | `INVALID_REQUEST` | 잘못된 요청입니다. | JSON 파싱, 타입 변환, DTO 검증 실패 |
| 400 | `INVALID_CURSOR` | 유효하지 않은 커서입니다. | cursor 형식 또는 정렬 기준 불일치 |
| 401 | `UNAUTHORIZED` | 인증이 필요합니다. | Access Token 누락 또는 서명 불일치 |
| 401 | `ACCESS_TOKEN_EXPIRED` | Access Token이 만료되었습니다. | Access Token 만료 |
| 401 | `INVALID_REFRESH_TOKEN` | 유효하지 않은 Refresh Token입니다. | Refresh Token 누락, 해시 불일치 또는 소유자 불일치 |
| 403 | `FORBIDDEN` | 요청 권한이 없습니다. | 같은 공유 그룹 안에서 역할 또는 소유권 부족 |
| 404 | `RESOURCE_NOT_FOUND` | 요청한 리소스를 찾을 수 없습니다. | 존재하지 않거나 접근할 수 없는 리소스 |
| 409 | `RESOURCE_CONFLICT` | 현재 상태와 충돌하는 요청입니다. | 중복 생성 또는 상태 충돌 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 다른 요청에 사용한 멱등성 키입니다. | 같은 key를 다른 요청 본문에 재사용 |
| 409 | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 동일한 요청을 처리하고 있습니다. | 같은 key의 최초 요청이 아직 처리 중 |
| 413 | `FILE_TOO_LARGE` | 업로드 가능한 파일 크기를 초과했습니다. | 업로드 URL 발급 요청의 선언한 파일 크기가 제한 초과 |
| 415 | `UNSUPPORTED_IMAGE_TYPE` | 지원하지 않는 이미지 형식입니다. | 업로드 URL 발급 요청의 `contentType`이 `image/*`가 아님 |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 내부 오류가 발생했습니다. | 처리하지 못한 예외 |

썸네일 생성 같은 업로드 완료 이후의 Object Storage 작업은 비동기로 처리하므로 동기 API 오류로 노출하지 않는다. 실패하면 `photo.thumbnailStatus`가 `FAILED`로 남고, 스윕이 재시도한다.

보안상 다른 공유 그룹의 리소스 ID를 사용한 경우 존재 여부를 노출하지 않도록 `404 RESOURCE_NOT_FOUND`로 응답한다. 같은 공유 그룹 안에서 소유권만 부족한 경우에는 `403` 도메인 오류를 사용한다.

## 7. 도메인 오류 코드

| HTTP Status | code | 적용 대상 |
|---:|---|---|
| 400 | `DISPLAY_NAME_REQUIRED` | 최초 Apple 가입 시 표시 이름 누락 |
| 400 | `INVALID_DISPLAY_NAME` | 사용자 표시 이름 검증 |
| 401 | `INVALID_APPLE_TOKEN` | Apple identity token 검증 |
| 401 | `INVALID_APPLE_AUTHORIZATION_CODE` | Apple authorization code 검증 |
| 401 | `REFRESH_TOKEN_EXPIRED` | Refresh Token 만료 |
| 401 | `REFRESH_TOKEN_REUSE_DETECTED` | 폐기·회전된 Refresh Token 재사용 |
| 404 | `USER_NOT_FOUND` | 사용자 |
| 400 | `INVALID_SHARED_GROUP_NAME` | 공유 그룹 이름 검증 |
| 404 | `SHARED_GROUP_NOT_FOUND` | 공유 그룹 |
| 403 | `ONLY_HOST_CAN_UPDATE_SHARED_GROUP` | 방장이 아닌 사용자의 공유 그룹 수정 |
| 403 | `ONLY_HOST_CAN_DELETE_SHARED_GROUP` | 방장이 아닌 사용자의 공유 그룹 삭제 |
| 403 | `HOST_CANNOT_LEAVE_SHARED_GROUP` | 방장의 공유 그룹 나가기 |
| 500 | `INVITE_CODE_GENERATION_FAILED` | 제한된 재시도 안에 고유 코드 예약 실패 |
| 400 | `INVALID_INVITE_CODE` | 존재하지 않거나 삭제된 공유 그룹의 초대 코드 |
| 409 | `ALREADY_JOINED_SHARED_GROUP` | 이미 활성 멤버십이 있는 사용자의 참여 |
| 400 | `INVALID_SHARED_ALBUM_NAME` | 앨범 이름 검증 |
| 400 | `INVALID_SHARED_ALBUM_IDS` | 공유집(앨범) 일괄 삭제 식별자 배열 검증 |
| 404 | `SHARED_ALBUM_NOT_FOUND` | 앨범 |
| 400 | `INVALID_UPLOAD_METADATA` | 사진 업로드 URL 발급·완료 등록 요청 검증 |
| 400 | `TOO_MANY_FILES` | 한 요청의 사진 개수 제한 초과 |
| 404 | `UPLOAD_OBJECT_NOT_FOUND` | `objectKey`에 대응하는 유효한 업로드 예약이 없음(발급받은 적 없음, 다른 사용자·다른 공유집(앨범)에 발급됨, 이미 등록에 사용함, 만료됨을 모두 포함) |
| 409 | `UPLOAD_NOT_COMPLETED` | 예약은 유효하지만 `objectKey`로 원본이 아직 업로드되지 않은 상태에서 완료 등록 시도 |
| 400 | `INVALID_TAKEN_AT` | 촬영일시 UTC ISO-8601 형식 검증 |
| 400 | `INVALID_PHOTO_LOCATION` | 사진 위치 필드 일부만 전달 |
| 400 | `INVALID_PHOTO_IDS` | 사진 추가·제거 식별자 배열 검증 |
| 404 | `PHOTO_NOT_FOUND` | 사진 |
| 403 | `NOT_PHOTO_UPLOADER` | 업로더가 아닌 사용자의 사진 수정·삭제 |
| 409 | `PHOTO_NOT_IN_SAME_SHARED_GROUP` | 공유집(앨범)과 다른 공유 그룹에 속한 사진을 추가 시도 |
| 400 | `INVALID_PHOTO_COMMENT_CONTENT` | 사진 댓글 내용 검증 |
| 400 | `INVALID_CURSOR` | 채팅 타임라인 cursor 검증 |
| 400 | `INVALID_CHAT_MESSAGE_CONTENT` | 그룹 채팅 메시지 내용 검증 |

## 8. 권한 규칙

| 리소스 | 생성 | 수정 | 삭제 |
|---|---|---|---|
| 공유 그룹 | 로그인 사용자 | 활성 방장 | 활성 방장 |
| 공유집(앨범) | 활성 방장·멤버 | 활성 방장·멤버 | 활성 방장·멤버 |
| 사진 | 활성 방장·멤버 | 업로더 | 업로더. 업로더가 탈퇴한 경우 공유 그룹 방장 |
| 앨범-사진 매핑 | 활성 방장·멤버 | 해당 없음 | 활성 방장·멤버 |
| 사진 좋아요 | 활성 방장·멤버 | 해당 없음 | 좋아요를 누른 사용자 |
| 사진 댓글 | 활성 방장·멤버 | MVP에서 제공하지 않음 | MVP에서 제공하지 않음 |
| 그룹 채팅 메시지 | 활성 방장·멤버 | MVP에서 제공하지 않음 | MVP에서 제공하지 않음 |

삭제된 공유 그룹의 모든 하위 데이터와 나간 멤버의 접근은 즉시 차단한다. 공유 그룹 삭제는 하위 공유집(앨범)·사진에도 같은 `deleted_at`을 기록하고, 사진 소속을 식별할 `shared_album_photo` 매핑은 30일 물리 정리 전까지 보존한다. 30일 뒤 배치는 각 사진의 Object Storage 원본·썸네일을 먼저 삭제하고 성공한 사진의 매핑·댓글·좋아요·사진 행을 물리 삭제한 뒤 공유 그룹과 초대 코드 예약을 삭제한다. 스토리지 삭제 실패 시 DB 행과 초대 코드 예약을 유지해 재시도한다. 사진·공유집(앨범)·공유 그룹은 soft delete 후 30일 뒤 물리 정리하며 사용자 복구 API는 제공하지 않는다. 사진은 공유 그룹에 직접 속하지 않고, `shared_album_photo`를 통해서만 하나 이상의 공유집(앨범)에 속한다. 사진은 항상 1개 이상의 공유집(앨범)에 속해야 하며, 마지막 소속이 없어지면 사진도 함께 soft delete된다.

MVP에는 사진 댓글과 그룹 채팅 메시지의 사용자 수정·삭제 API가 없다. 사진 댓글은 사진 물리 정리 시 삭제하고, 그룹 채팅 메시지는 공유 그룹 물리 삭제 시 FK cascade로 정리한다.

## 9. 사진 업로드와 이미지 URL 정책

원본 이미지는 서버를 거치지 않고 iOS와 OCI Object Storage 사이에서 직접 오간다. 서버는 제어 평면(메타데이터, presigned URL 발급)만 담당한다.

업로드 흐름:

1. iOS가 [PHOTO-02 사진 업로드 URL 발급](07-photo-management.md#3-photo-02-사진-업로드-url-발급)을 호출해 파일별 `objectKey`와 presigned PUT URL을 받는다.
2. iOS가 발급받은 URL로 원본을 Object Storage에 직접 병렬 업로드한다.
3. iOS가 [PHOTO-03 사진 업로드 완료 등록](07-photo-management.md#4-photo-03-사진-업로드-완료-등록)을 호출해 `photo` 행을 생성한다. 이때 `thumbnailStatus`는 `PENDING`으로 시작한다.
4. 서버가 비동기로 원본을 다운로드해 썸네일을 생성하고 Object Storage에 업로드한 뒤 `thumbnailStatus`를 `READY` 또는 `FAILED`로 갱신한다.

이미지 URL 정책:

- `photo`는 원본·썸네일 URL이 아니라 Object Storage 객체 키(`originalObjectKey`, `thumbnailObjectKey`)만 저장한다.
- API는 사진을 반환할 때마다 해당 객체 키로 presigned GET URL(`originalUrl`, `thumbnailUrl`)을 새로 발급하고, 만료 시각(`originalUrlExpiresAt`, `thumbnailUrlExpiresAt`)을 함께 반환한다.
- 클라이언트는 응답받은 URL을 영구 저장하지 않는다. 만료되면 목록 또는 상세 API를 다시 호출해 갱신한다.
- URL 유효 시간은 운영 설정이며 API 계약은 만료 시각 필드로만 보장한다.
- `thumbnailStatus`가 `PENDING`이거나 `FAILED`면 `thumbnailUrl`, `thumbnailUrlExpiresAt`은 `null`이다.

## 10. 사진 업로드 초안 정책

데이터 모델만으로 확정할 수 없는 운영 제한은 다음 초안값을 사용한다.

| 항목 | 초안값 |
|---|---|
| 한 요청의 최대 사진 수 | 20개 |
| 사진 한 장 최대 크기 | 20 MiB. 업로드 URL 발급 요청에 선언한 크기가 초과하면 `FILE_TOO_LARGE`, 실제 업로드 바이트 크기 제한은 presigned URL 서명 조건으로 강제 |
| 허용 MIME type | `image/*` |
| 촬영 기기명·촬영일시·위치·이미지 크기 | iOS가 EXIF에서 추출해 업로드 완료 등록 요청에 실어 보낸다. 서버는 추론하지 않고 전달받은 값만 저장하며, 없으면 `null`로 보존한다 |
| 썸네일 | 완료 등록 직후 서버가 비동기로 생성한다. 재시작으로 작업이 유실되면 `thumbnailStatus=PENDING` 상태의 사진을 주기적 스윕이 재제출한다 |
| 원본 이미지 형식 | iOS가 JPEG로 업로드해 서버의 HEIC 디코딩을 피한다 |

운영 인프라와 iOS 메모리 검증 후 제한값을 확정해야 한다. 제한값이 바뀌어도 DB 스키마 변경은 필요하지 않다.
