# 사진 상세·반응 API 명세

## 1. Notion DB 등록 정보

| ID | index | 이름 | HTTP Method | API Path | 상태 | 구현 여부 | 연동 o/x |
|---|---|---|---|---|---|---|---|
| REACTION-01 | 사진 반응·댓글 | 사진 상세 조회 | GET | `/api/v1/photos/{photoId}` | 완료 | true | false |
| REACTION-02 | 사진 반응·댓글 | 사진 좋아요 설정 | PUT | `/api/v1/photos/{photoId}/like` | 완료 | true | false |
| REACTION-03 | 사진 반응·댓글 | 사진 좋아요 취소 | DELETE | `/api/v1/photos/{photoId}/like` | 완료 | true | false |
| COMMENT-01 | 사진 반응·댓글 | 사진 댓글 목록 조회 | GET | `/api/v1/photos/{photoId}/comments` | 완료 | true | false |
| COMMENT-02 | 사진 반응·댓글 | 사진 댓글 작성 | POST | `/api/v1/photos/{photoId}/comments` | 완료 | true | false |

모든 API는 대상 공유 그룹의 활성 멤버십을 요구한다. 사진 댓글은 사진 상세에서 독립적으로 조회할 수 있으며, 같은 공유 그룹의 채팅 타임라인 조회(CHAT-01)에도 일반 채팅 메시지와 함께 시간순으로 표시된다. 공통 응답과 오류는 [01-common-spec.md](01-common-spec.md)를 따른다.

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
| `cursor` | String | X | null | 이전 응답의 불투명 cursor |
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
| 404 | `PHOTO_NOT_FOUND` | 활성 사진 또는 상위 리소스·멤버십이 없음 |

## 6. COMMENT-02 사진 댓글 작성

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Idempotency-Key` | UUID String | O | 댓글 작성 재시도 식별자 | `54cf8d7e-a23e-4e76-90f7-603f122b1507` |

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
| 400 | `INVALID_PHOTO_COMMENT_CONTENT` | 본문이 공백이거나 1,000자를 초과함 |
| 404 | `PHOTO_NOT_FOUND` | 활성 사진 또는 상위 리소스·멤버십이 없음 |
