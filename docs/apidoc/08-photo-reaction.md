# 사진 상세·반응 API 명세

## 1. Notion DB 등록 정보

| ID | index | 이름 | HTTP Method | API Path | 상태 | 구현 여부 | 연동 o/x |
|---|---|---|---|---|---|---|---|
| REACTION-01 | 사진 반응·댓글 | 사진 상세 조회 | GET | `/api/v1/photos/{photoId}` | 완료 | true | false |
| REACTION-02 | 사진 반응·댓글 | 사진 좋아요 설정 | PUT | `/api/v1/photos/{photoId}/like` | 완료 | true | false |
| REACTION-03 | 사진 반응·댓글 | 사진 좋아요 취소 | DELETE | `/api/v1/photos/{photoId}/like` | 완료 | true | false |
| COMMENT-01 | 사진 반응·댓글 | 사진 댓글 목록 조회 | GET | `/api/v1/photos/{photoId}/comments` | 완료 | true | false |
| COMMENT-02 | 사진 반응·댓글 | 사진 댓글 작성 | POST | `/api/v1/photos/{photoId}/comments` | 완료 | true | false |

모든 API는 `Authorization: Bearer <accessToken>` 헤더와 대상 공유 그룹의 활성 멤버십을 요구한다. `accessToken`에는 로그인 또는 토큰 갱신 응답에서 받은 값을 사용한다. 사진 댓글은 사진 상세에서 독립적으로 조회할 수 있으며, 같은 공유 그룹의 채팅 타임라인 조회(CHAT-01)에도 일반 채팅 메시지와 함께 시간순으로 표시된다. 공통 응답과 오류는 [01-common-spec.md](01-common-spec.md)를 따른다.

MVP에서는 사진 댓글의 작성과 조회만 제공한다. 댓글 수정·삭제 API는 후속 범위다.

## 2. REACTION-01 사진 상세 조회

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `photoId` | UUID | O | 사진 식별자 |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "PHOTO_FOUND",
  "message": "사진을 조회했습니다.",
  "data": {
    "id": "385ff765-b20c-49a2-8e62-e1457784aa15",
    "sharedGroupId": "b8a5f612-25d7-4ec3-9d1d-59684de40664",
    "sharedAlbumIds": ["59ce0d18-a53e-4197-9c3c-e82331adc097"],
    "originalUrl": "https://objectstorage.example.com/signed/385ff765-original.jpg",
    "originalUrlExpiresAt": "2026-07-03T11:15:30Z",
    "thumbnailUrl": "https://objectstorage.example.com/signed/385ff765-thumb.jpg",
    "thumbnailUrlExpiresAt": "2026-07-03T11:15:30Z",
    "thumbnailStatus": "READY",
    "deviceModel": "iPhone 15",
    "takenAt": "2026-06-30T04:20:00Z",
    "displayAt": "2026-06-30T04:20:00Z",
    "latitude": 33.450701,
    "longitude": 126.570667,
    "locationName": "제주특별자치도 제주시",
    "isInferred": false,
    "width": 4032,
    "height": 3024,
    "uploadedBy": {
      "userId": "018f0c3e-2c77-7d72-a37e-2f5666f25d32",
      "displayName": "집집이"
    },
    "isUploader": true,
    "likeCount": 3,
    "commentCount": 2,
    "isLikedByMe": true,
    "createdAt": "2026-07-03T10:15:30Z",
    "updatedAt": "2026-07-03T10:15:30Z"
  }
}
```

`sharedAlbumIds`는 사진이 현재 속한 모든 활성 공유집(앨범) 식별자 목록이다.
`originalUrl`, `thumbnailUrl`은 조회 시점마다 새로 발급하는 presigned URL이며 영구 저장하지 않는다.
`thumbnailStatus`가 `PENDING`이거나 `FAILED`면 `thumbnailUrl`, `thumbnailUrlExpiresAt`은 `null`이다.
`deviceModel`, `takenAt`, `latitude`, `longitude`, `locationName`, `width`, `height`는 iOS가 전달하지 않았으면 `null`이다.

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `PHOTO_NOT_FOUND` | 활성 사진 또는 상위 리소스·멤버십이 없음 |

## 3. REACTION-02 사진 좋아요 설정

좋아요가 이미 있으면 새 행을 만들지 않고 현재 상태를 반환하는 멱등 API다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `photoId` | UUID | O | 사진 식별자 |

요청 본문은 없다.

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "PHOTO_LIKED",
  "message": "사진에 좋아요를 설정했습니다.",
  "data": {
    "photoId": "385ff765-b20c-49a2-8e62-e1457784aa15",
    "isLikedByMe": true,
    "likeCount": 4
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `PHOTO_NOT_FOUND` | 활성 사진 또는 상위 리소스·멤버십이 없음 |

## 4. REACTION-03 사진 좋아요 취소

좋아요가 없어도 현재 상태를 반환하는 멱등 API다. 존재하는 좋아요 행은 즉시 물리 삭제한다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `photoId` | UUID | O | 사진 식별자 |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "PHOTO_UNLIKED",
  "message": "사진 좋아요를 취소했습니다.",
  "data": {
    "photoId": "385ff765-b20c-49a2-8e62-e1457784aa15",
    "isLikedByMe": false,
    "likeCount": 3
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `PHOTO_NOT_FOUND` | 활성 사진 또는 상위 리소스·멤버십이 없음 |

## 5. COMMENT-01 사진 댓글 목록 조회

작성일시 오름차순으로 조회하며 동일 시각은 댓글 ID로 순서를 고정한다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `photoId` | UUID | O | 사진 식별자 |

#### Query

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---:|---|---|
| `cursor` | String | X | null | 이전 응답의 `nextCursor`를 수정하지 않고 그대로 전달하는 불투명 cursor |
| `size` | Integer | X | 20 | 1~100 |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "PHOTO_COMMENT_LIST_FOUND",
  "message": "사진 댓글 목록을 조회했습니다.",
  "data": {
    "items": [
      {
        "id": "22b50c69-ce29-4715-9825-dd27f4868bea",
        "content": "사진 너무 좋다!",
        "author": {
          "userId": "018f0c3e-2c77-7d72-a37e-2f5666f25d32",
          "displayName": "집집이"
        },
        "isAuthor": true,
        "createdAt": "2026-07-03T10:15:30Z",
        "updatedAt": "2026-07-03T10:15:30Z"
      }
    ],
    "nextCursor": null,
    "hasNext": false
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_CURSOR` | cursor가 유효하지 않음 |
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `PHOTO_NOT_FOUND` | 활성 사진 또는 상위 리소스·멤버십이 없음 |

## 6. COMMENT-02 사진 댓글 작성

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Idempotency-Key` | UUID String | O | 클라이언트가 생성하는 댓글 작성 재시도 식별자. 같은 논리적 요청 재시도에는 같은 UUID와 동일한 요청 본문을 사용한다. | `54cf8d7e-a23e-4e76-90f7-603f122b1507` |

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `photoId` | UUID | O | 사진 식별자 |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---:|---|---|
| `content` | String | O | trim 후 1~1,000자 | 댓글 본문 |

### Success Response ✓

#### HTTP Status Code: `201 Created`

같은 `Idempotency-Key`와 같은 요청을 재시도해 저장된 성공 응답을 받은 경우에만 `Idempotency-Replayed: true` 응답 헤더가 포함된다.

```json
{
  "status": 201,
  "code": "PHOTO_COMMENT_CREATED",
  "message": "사진 댓글을 작성했습니다.",
  "data": {
    "id": "22b50c69-ce29-4715-9825-dd27f4868bea",
    "photoId": "385ff765-b20c-49a2-8e62-e1457784aa15",
    "content": "사진 너무 좋다!",
    "author": {
      "userId": "018f0c3e-2c77-7d72-a37e-2f5666f25d32",
      "displayName": "집집이"
    },
    "createdAt": "2026-07-03T10:15:30Z"
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_REQUEST` | `Idempotency-Key` 누락·UUID 형식 오류 또는 요청 본문 형식 오류 |
| 400 | `INVALID_PHOTO_COMMENT_CONTENT` | 본문이 공백이거나 1,000자를 초과함 |
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `PHOTO_NOT_FOUND` | 활성 사진 또는 상위 리소스·멤버십이 없음 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 `Idempotency-Key`를 다른 댓글 작성 요청에 재사용함 |
| 409 | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 요청이 아직 처리 중임 |
