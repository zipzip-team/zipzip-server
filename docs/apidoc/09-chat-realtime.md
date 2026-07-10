# 그룹 채팅 타임라인 API 명세

## 1. Notion DB 등록 정보

| ID | index | 이름 | HTTP Method | API Path | 상태 | 구현 여부 | 연동 o/x |
|---|---|---|---|---|---|---|---|
| CHAT-01 | 채팅 | 그룹 채팅 타임라인 조회 | GET | `/api/v1/shared-groups/{sharedGroupId}/chat-messages` | 완료 | true | false |
| CHAT-02 | 채팅 | 그룹 채팅 메시지 작성 | POST | `/api/v1/shared-groups/{sharedGroupId}/chat-messages` | 완료 | true | false |
| CHAT-03 | 채팅 | 그룹 채팅 메시지 수정 | PATCH | `/api/v1/shared-group-chat-messages/{messageId}` | 시작 전 | false | false |
| CHAT-04 | 채팅 | 그룹 채팅 메시지 삭제 | DELETE | `/api/v1/shared-group-chat-messages/{messageId}` | 시작 전 | false | false |

공유 그룹 하나가 하나의 채팅방이다. 따라서 별도 `chat_room` 테이블이나 채팅방 조회 API는 두지 않고, `sharedGroupId`를 채팅방 식별자로 사용한다.
일반 채팅 메시지는 `shared_group_chat_message`, 사진 댓글은 `photo_comment`에 각각 저장한다. CHAT-01은 대상 공유 그룹의 일반 채팅 메시지와 활성 사진 댓글을 하나의 타임라인으로 병합해 반환한다.
1차 구현은 폴링 조회를 기준으로 하며 WebSocket 실시간 전달은 후속 범위로 둔다.

## 2. CHAT-01 그룹 채팅 타임라인 조회

대상 공유 그룹의 일반 채팅 메시지와, 대상 공유 그룹의 활성 사진에 작성된 댓글을 하나의 타임라인으로 조회한다. 사진 댓글의 소속 공유 그룹은 `photo -> shared_album_photo -> shared_album` 경로로 판별한다.

항목은 `createdAt` 내림차순, 같은 시각이면 `type` 오름차순(`CHAT_MESSAGE`가 먼저), 같은 타입이면 항목 ID 내림차순으로 정렬한다. 최초 페이지는 최신 항목부터 반환하며, 화면에서 시간순으로 표시할 때는 반환 배열을 역순 배치할 수 있다.

`cursor`는 마지막 항목의 `createdAt`, `type`, `id`를 포함하는 불투명 값이다. 클라이언트는 응답의 `nextCursor`를 그대로 다음 요청에 전달해야 하며, 값을 조합하거나 해석하지 않는다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedGroupId` | UUID | O | 채팅방으로 사용할 공유 그룹 식별자 |

#### Query

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---:|---|---|
| `cursor` | String | X | null | 이전 응답의 불투명 타임라인 cursor |
| `size` | Integer | X | 30 | 1~100 |

### Success Response

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "SHARED_GROUP_CHAT_TIMELINE_FOUND",
  "message": "그룹 채팅 타임라인을 조회했습니다.",
  "data": {
    "items": [
      {
        "type": "CHAT_MESSAGE",
        "id": "22b50c69-ce29-4715-9825-dd27f4868bea",
        "content": "이번 여행 사진 올려줘!",
        "author": {
          "userId": "018f0c3e-2c77-7d72-a37e-2f5666f25d32",
          "displayName": "집집이"
        },
        "isAuthor": true,
        "createdAt": "2026-07-03T10:15:30Z",
        "updatedAt": "2026-07-03T10:15:30Z"
      },
      {
        "type": "PHOTO_COMMENT",
        "id": "93f1cdb0-8ca8-4d05-a9c7-2c44b18ed326",
        "photoId": "385ff765-b20c-49a2-8e62-e1457784aa15",
        "content": "사진 너무 좋다!",
        "author": {
          "userId": "018f0c3e-2c77-7d72-a37e-2f5666f25d32",
          "displayName": "집집이"
        },
        "isAuthor": true,
        "createdAt": "2026-07-03T10:14:30Z",
        "updatedAt": "2026-07-03T10:14:30Z"
      }
    ],
    "nextCursor": null,
    "hasNext": false
  }
}
```

### Fail Response

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_REQUEST` | `size`가 1~100 범위를 벗어남 |
| 400 | `INVALID_CURSOR` | cursor가 유효하지 않음 |
| 404 | `SHARED_GROUP_NOT_FOUND` | 공유 그룹 또는 활성 멤버십이 없음 |

`type`이 `CHAT_MESSAGE`이면 일반 채팅 메시지이고, `PHOTO_COMMENT`이면 사진 상세에 작성된 댓글이다. `PHOTO_COMMENT`의 `photoId`는 댓글이 연결된 사진 식별자이며, 일반 채팅 메시지에서는 `null`이다.

사진 댓글의 수정·삭제는 이 API가 아니라 COMMENT-03, COMMENT-04를 사용한다. 사진이 soft delete되었거나 대상 공유 그룹에 더 이상 활성 소속이 없으면 해당 사진의 댓글은 타임라인에서 반환하지 않는다.

## 3. CHAT-02 그룹 채팅 메시지 작성

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Idempotency-Key` | UUID String | O | 메시지 작성 재시도 식별자 | `54cf8d7e-a23e-4e76-90f7-603f122b1507` |

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedGroupId` | UUID | O | 메시지를 작성할 공유 그룹 식별자 |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---:|---|---|
| `content` | String | O | trim 후 1~1,000자 | 메시지 본문 |

### Success Response

#### HTTP Status Code: `201 Created`

```json
{
  "status": 201,
  "code": "SHARED_GROUP_CHAT_MESSAGE_CREATED",
  "message": "그룹 채팅 메시지를 작성했습니다.",
  "data": {
    "id": "22b50c69-ce29-4715-9825-dd27f4868bea",
    "content": "이번 여행 사진 올려줘!",
    "author": {
      "userId": "018f0c3e-2c77-7d72-a37e-2f5666f25d32",
      "displayName": "집집이"
    },
    "isAuthor": true,
    "createdAt": "2026-07-03T10:15:30Z",
    "updatedAt": "2026-07-03T10:15:30Z"
  }
}
```

### Fail Response

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_REQUEST` | `Idempotency-Key`가 누락됐거나 UUID 형식이 아님 |
| 400 | `INVALID_CHAT_MESSAGE_CONTENT` | 본문이 공백이거나 1,000자를 초과함 |
| 404 | `SHARED_GROUP_NOT_FOUND` | 공유 그룹 또는 활성 멤버십이 없음 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 키가 다른 메시지 작성 요청에 이미 사용됨 |
| 409 | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 키의 메시지 작성 요청을 처리 중임 |

## 4. CHAT-03 그룹 채팅 메시지 수정

작성자만 수정할 수 있다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `messageId` | UUID | O | 그룹 채팅 메시지 식별자 |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---:|---|---|
| `content` | String | O | trim 후 1~1,000자 | 새 메시지 본문 |

### Success Response

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "SHARED_GROUP_CHAT_MESSAGE_UPDATED",
  "message": "그룹 채팅 메시지를 수정했습니다.",
  "data": {
    "id": "22b50c69-ce29-4715-9825-dd27f4868bea",
    "content": "이번 여행 사진 올려줘!",
    "updatedAt": "2026-07-03T10:20:30Z"
  }
}
```

### Fail Response

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_CHAT_MESSAGE_CONTENT` | 본문이 공백이거나 1,000자를 초과함 |
| 403 | `NOT_CHAT_MESSAGE_AUTHOR` | 활성 멤버지만 작성자가 아님 |
| 404 | `CHAT_MESSAGE_NOT_FOUND` | 메시지 또는 상위 활성 리소스·멤버십이 없음 |

## 5. CHAT-04 그룹 채팅 메시지 삭제

작성자만 삭제할 수 있다.
휴지통 없이 `shared_group_chat_message` 행을 즉시 물리 삭제한다. 복구할 수 없다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `messageId` | UUID | O | 그룹 채팅 메시지 식별자 |

### Success Response

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "SHARED_GROUP_CHAT_MESSAGE_DELETED",
  "message": "그룹 채팅 메시지를 삭제했습니다.",
  "data": null
}
```

### Fail Response

| HTTP Status | code | 조건 |
|---:|---|---|
| 403 | `NOT_CHAT_MESSAGE_AUTHOR` | 활성 멤버지만 작성자가 아님 |
| 404 | `CHAT_MESSAGE_NOT_FOUND` | 메시지 또는 상위 활성 리소스·멤버십이 없음 |
