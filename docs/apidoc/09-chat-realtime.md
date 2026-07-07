# 그룹 채팅 API 명세

## 1. Notion DB 등록 정보

| ID | index | 이름 | HTTP Method | API Path | 상태 | 구현 여부 | 연동 o/x |
|---|---|---|---|---|---|---|---|
| CHAT-01 | 채팅 | 그룹 채팅 메시지 목록 조회 | GET | `/api/v1/shared-groups/{sharedGroupId}/chat-messages` | 시작 전 | false | false |
| CHAT-02 | 채팅 | 그룹 채팅 메시지 작성 | POST | `/api/v1/shared-groups/{sharedGroupId}/chat-messages` | 시작 전 | false | false |
| CHAT-03 | 채팅 | 그룹 채팅 메시지 수정 | PATCH | `/api/v1/shared-group-chat-messages/{messageId}` | 시작 전 | false | false |
| CHAT-04 | 채팅 | 그룹 채팅 메시지 삭제 | DELETE | `/api/v1/shared-group-chat-messages/{messageId}` | 시작 전 | false | false |

그룹 채팅 메시지는 사진 댓글과 별도인 `shared_group_chat_message`에 저장한다.
1차 구현은 폴링 조회를 기준으로 하며 WebSocket 실시간 전달은 후속 범위로 둔다.

## 2. CHAT-01 그룹 채팅 메시지 목록 조회

공유 그룹에 속한 활성 그룹 채팅 메시지를 `createdAt`, 메시지 ID 내림차순으로 조회한다.
최초 페이지는 최신 메시지부터 반환하며, 화면에서 시간순으로 표시할 때는 반환 배열을 역순 배치할 수 있다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedGroupId` | UUID | O | 채팅 메시지를 조회할 공유 그룹 식별자 |

#### Query

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---:|---|---|
| `cursor` | String | X | null | 이전 응답의 불투명 cursor |
| `size` | Integer | X | 30 | 1~100 |

### Success Response

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "SHARED_GROUP_CHAT_MESSAGE_LIST_FOUND",
  "message": "그룹 채팅 메시지 목록을 조회했습니다.",
  "data": {
    "items": [
      {
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
    ],
    "nextCursor": null,
    "hasNext": false
  }
}
```

### Fail Response

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_CURSOR` | cursor가 유효하지 않음 |
| 404 | `SHARED_GROUP_NOT_FOUND` | 공유 그룹 또는 활성 멤버십이 없음 |

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
| 400 | `INVALID_CHAT_MESSAGE_CONTENT` | 본문이 공백이거나 1,000자를 초과함 |
| 404 | `SHARED_GROUP_NOT_FOUND` | 공유 그룹 또는 활성 멤버십이 없음 |

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
삭제 시 `shared_group_chat_message.deleted_at`을 기록한다.

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
