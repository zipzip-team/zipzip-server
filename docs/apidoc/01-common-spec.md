# Zipzip API 공통 규격

## 1. 기본 규칙

| 항목 | 규칙 |
|---|---|
| Base Path | `/api/v1` |
| 기본 요청 형식 | `application/json` |
| 사진 업로드 형식 | `multipart/form-data` |
| 인증 | `Authorization: Bearer {accessToken}` |
| 식별자 | UUID 문자열 |
| 시간 | UTC ISO-8601 문자열. 예: `2026-07-03T10:15:30Z` |
| JSON 필드명 | `camelCase` |
| 목록 페이지네이션 | 불투명 cursor 기반 |
| 삭제 응답 | `200 OK`와 `data: null` |

Apple 로그인과 토큰 갱신을 제외한 모든 API는 인증이 필요하다. 인증된 사용자라도 공유 폴더 내부 리소스에는 해당 공유 폴더의 활성 멤버십이 있어야 한다.

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
- DEVICE-02 내 기기 등록
- FOLDER-02 공유 폴더 생성
- INVITE-02 초대 코드로 참여
- ALBUM-02 앨범 생성
- PHOTO-02 사진 업로드
- PHOTO-05 사진 일괄 삭제
- COMMENT-02 사진 댓글 작성

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
| 403 | `FORBIDDEN` | 요청 권한이 없습니다. | 같은 공유 폴더 안에서 역할 또는 소유권 부족 |
| 404 | `RESOURCE_NOT_FOUND` | 요청한 리소스를 찾을 수 없습니다. | 존재하지 않거나 접근할 수 없는 리소스 |
| 409 | `RESOURCE_CONFLICT` | 현재 상태와 충돌하는 요청입니다. | 중복 생성 또는 상태 충돌 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 다른 요청에 사용한 멱등성 키입니다. | 같은 key를 다른 요청 본문에 재사용 |
| 409 | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 동일한 요청을 처리하고 있습니다. | 같은 key의 최초 요청이 아직 처리 중 |
| 413 | `FILE_TOO_LARGE` | 업로드 가능한 파일 크기를 초과했습니다. | 업로드 제한 초과 |
| 415 | `UNSUPPORTED_IMAGE_TYPE` | 지원하지 않는 이미지 형식입니다. | `image/*`가 아닌 파일 |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 내부 오류가 발생했습니다. | 처리하지 못한 예외 |
| 502 | `OBJECT_STORAGE_UPLOAD_FAILED` | 사진 저장에 실패했습니다. | OCI Object Storage 업로드 실패 |

보안상 다른 공유 폴더의 리소스 ID를 사용한 경우 존재 여부를 노출하지 않도록 `404 RESOURCE_NOT_FOUND`로 응답한다. 같은 공유 폴더 안에서 소유권만 부족한 경우에는 `403` 도메인 오류를 사용한다.

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
| 400 | `INVALID_DEVICE_NAME` | 기기명 검증 |
| 404 | `DEVICE_NOT_FOUND` | 기기 |
| 409 | `DEVICE_ALREADY_EXISTS` | 동일 사용자의 활성 기기명 중복 |
| 400 | `INVALID_SHARED_FOLDER_NAME` | 공유 폴더 이름 검증 |
| 404 | `SHARED_FOLDER_NOT_FOUND` | 공유 폴더 |
| 403 | `ONLY_HOST_CAN_UPDATE_SHARED_FOLDER` | 방장이 아닌 사용자의 공유 폴더 수정 |
| 403 | `ONLY_HOST_CAN_DELETE_SHARED_FOLDER` | 방장이 아닌 사용자의 공유 폴더 삭제 |
| 403 | `HOST_CANNOT_LEAVE_SHARED_FOLDER` | 방장의 공유 폴더 나가기 |
| 500 | `INVITE_CODE_GENERATION_FAILED` | 제한된 재시도 안에 고유 코드 예약 실패 |
| 400 | `INVALID_INVITE_CODE` | 존재하지 않거나 삭제된 공유 폴더의 초대 코드 |
| 409 | `ALREADY_JOINED_SHARED_FOLDER` | 이미 활성 멤버십이 있는 사용자의 참여 |
| 400 | `INVALID_SHARED_ALBUM_NAME` | 앨범 이름 검증 |
| 404 | `SHARED_ALBUM_NOT_FOUND` | 앨범 |
| 403 | `NOT_SHARED_ALBUM_CREATOR` | 생성자가 아닌 사용자의 앨범 삭제 |
| 400 | `INVALID_UPLOAD_METADATA` | 사진 파일과 업로드 메타데이터 매핑 검증 |
| 400 | `TOO_MANY_FILES` | 한 요청의 사진 개수 제한 초과 |
| 400 | `INVALID_TAKEN_AT` | 촬영일시 UTC ISO-8601 형식 검증 |
| 400 | `INVALID_PHOTO_IDS` | 사진 일괄 삭제 식별자 배열 검증 |
| 404 | `PHOTO_NOT_FOUND` | 사진 |
| 403 | `NOT_PHOTO_UPLOADER` | 업로더가 아닌 사용자의 사진 수정·삭제 |
| 400 | `INVALID_PHOTO_COMMENT_CONTENT` | 사진 댓글 내용 검증 |
| 404 | `PHOTO_COMMENT_NOT_FOUND` | 사진 댓글 |
| 403 | `NOT_PHOTO_COMMENT_AUTHOR` | 작성자가 아닌 사용자의 댓글 수정·삭제 |
| - | `INVALID_EVENT` | WebSocket JSON 또는 필수 event 필드 검증 |
| - | `TOO_MANY_SUBSCRIPTIONS` | WebSocket 연결당 공유 폴더 구독 수 제한 초과 |

## 8. 권한 규칙

| 리소스 | 생성 | 수정 | 삭제 |
|---|---|---|---|
| 공유 폴더 | 로그인 사용자 | 활성 방장 | 활성 방장 |
| 앨범 | 활성 방장·멤버 | 활성 방장·멤버 | 생성자 |
| 앨범 사진 포함 관계 | 활성 방장·멤버 | 해당 없음 | 활성 방장·멤버 |
| 사진 | 활성 방장·멤버 | 업로더 | 업로더 |
| 사진 좋아요 | 활성 방장·멤버 | 해당 없음 | 좋아요를 누른 사용자 |
| 사진 댓글 | 활성 방장·멤버 | 작성자 | 작성자 |

삭제된 공유 폴더의 모든 하위 데이터와 나간 멤버의 접근은 즉시 차단한다. 사진·앨범·공유 폴더는 soft delete 후 30일 뒤 물리 정리하며 사용자 복구 API는 제공하지 않는다. 앨범에서만 사진을 제거하는 경우에는 포함 관계를 즉시 물리 삭제한다.

## 9. 이미지 URL 정책

API는 내부 `objectKey`를 노출하지 않고 만료 가능한 서명 URL을 `imageUrl`로 반환한다. 모든 `imageUrl` 응답에는 만료 시각인 `imageUrlExpiresAt`을 함께 반환한다.

- 클라이언트는 서명 URL을 영구 저장하지 않는다.
- URL이 만료되면 사진 목록 또는 상세 API를 다시 호출해 갱신한다.
- 기본 URL 유효 시간은 운영 설정으로 관리하며 API 계약은 만료 시각 필드로만 보장한다.

## 10. 사진 업로드 초안 정책

데이터 모델만으로 확정할 수 없는 운영 제한은 다음 초안값을 사용한다.

| 항목 | 초안값 |
|---|---|
| 한 요청의 최대 사진 수 | 20개 |
| 사진 한 장 최대 크기 | 20 MiB |
| 허용 MIME type | `image/*` |
| 촬영일시 | 서버가 EXIF에서 추출. 없으면 `takenAt=null`로 보존하고 `createdAt`을 표시·정렬에 사용 |

운영 인프라와 iOS 메모리 검증 후 제한값을 확정해야 한다. 제한값이 바뀌어도 DB 스키마 변경은 필요하지 않다.
