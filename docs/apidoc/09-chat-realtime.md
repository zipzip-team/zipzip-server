# 채팅 뷰·실시간 API 명세

## 1. Notion DB 등록 정보

| ID | index | 이름 | HTTP Method | API Path | 상태 | 구현 여부 | 연동 o/x |
|---|---|---|---|---|---|---|---|
| CHAT-01 | 채팅 | 채팅 뷰 댓글 목록 조회 | GET | `/api/v1/shared-folders/{sharedFolderId}/chat-comments` | 시작 전 | false | false |
| CHAT-WS-01 | 채팅 | 사진 댓글 이벤트 실시간 구독 | WS | `/api/v1/ws` | 시작 전 | false | false |

채팅 뷰는 별도 `chat_room`이나 `chat_message`를 저장하지 않는다. 공유 폴더의 `photo_comment`를 사진 정보와 함께 모아 보여주는 조회 모델이며, 작성·수정·삭제는 [사진 댓글 API](08-photo-reaction.md)를 그대로 사용한다. 사진 컨텍스트가 없는 독립 메시지는 1차 범위에 포함하지 않는다.

## 2. CHAT-01 채팅 뷰 댓글 목록 조회

공유 폴더에 속한 활성 사진의 활성 댓글을 `createdAt`, 댓글 ID 내림차순으로 조회한다. 최초 페이지는 최신 댓글부터 반환하며, 화면에서 시간순으로 표시할 때는 반환 배열을 역순 배치할 수 있다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedFolderId` | UUID | O | 채팅 뷰를 조회할 공유 폴더 식별자 |

#### Query

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---:|---|---|
| `cursor` | String | X | null | 이전 응답의 불투명 cursor |
| `size` | Integer | X | 30 | 1~100 |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "CHAT_VIEW_COMMENT_LIST_FOUND",
  "message": "채팅 뷰 댓글 목록을 조회했습니다.",
  "data": {
    "items": [
      {
        "id": "22b50c69-ce29-4715-9825-dd27f4868bea",
        "photo": {
          "id": "385ff765-b20c-49a2-8e62-e1457784aa15",
          "imageUrl": "https://object.example.com/signed/photo.jpg",
          "imageUrlExpiresAt": "2026-07-03T11:15:30Z"
        },
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
| 404 | `SHARED_FOLDER_NOT_FOUND` | 공유 폴더 또는 활성 멤버십이 없음 |

## 3. 댓글 작성·수정·삭제 계약

채팅 뷰에서 쓰는 모든 글은 특정 사진에 종속된 `photo_comment`다.

| 행위 | 사용할 API | 실시간 이벤트 |
|---|---|---|
| 작성 | `POST /api/v1/photos/{photoId}/comments` | `PHOTO_COMMENT_CREATED` |
| 수정 | `PATCH /api/v1/photo-comments/{commentId}` | `PHOTO_COMMENT_UPDATED` |
| 삭제 | `DELETE /api/v1/photo-comments/{commentId}` | `PHOTO_COMMENT_DELETED` |

본문은 trim 후 1~1,000자이며 작성자만 수정·삭제할 수 있다. REST 트랜잭션이 커밋된 뒤 대상 공유 폴더 구독자에게 이벤트를 발행한다.

## 4. CHAT-WS-01 사진 댓글 이벤트 실시간 구독

WebSocket은 REST로 생성·수정·삭제된 사진 댓글 결과를 같은 공유 폴더의 활성 멤버에게 전달하는 읽기 이벤트 채널이다.

### Connection Request

| 항목 | 값 |
|---|---|
| URL | `/api/v1/ws` |
| 인증 | 연결 Upgrade 요청의 `Authorization: Bearer {accessToken}` |
| 데이터 형식 | UTF-8 JSON text frame |
| heartbeat | 서버가 30초마다 ping, 클라이언트는 10초 안에 pong 응답 |
| 구독 수 | 연결 하나당 최대 20개 공유 폴더 |

Access Token을 URL query에 넣지 않는다. 연결 중 토큰이 만료되면 서버는 `4401`로 종료하고, 클라이언트는 토큰을 갱신한 뒤 새 연결을 만든다.

### Subscribe Request

```json
{
  "type": "SUBSCRIBE_SHARED_FOLDER_COMMENTS",
  "requestId": "9c153301-6c1e-4a86-b983-f370fa5fcc65",
  "sharedFolderId": "b8a5f612-25d7-4ec3-9d1d-59684de40664"
}
```

서버는 구독 시점과 각 이벤트 전송 시점에 활성 멤버십을 확인한다. 성공 시 `SHARED_FOLDER_COMMENTS_SUBSCRIBED`, 구독 해제 시 `SHARED_FOLDER_COMMENTS_UNSUBSCRIBED`를 반환한다.

### Comment Events

| type | 발생 조건 | payload 핵심 필드 |
|---|---|---|
| `PHOTO_COMMENT_CREATED` | 댓글 생성 커밋 완료 | 사진 요약을 포함한 전체 댓글 객체 |
| `PHOTO_COMMENT_UPDATED` | 댓글 수정 커밋 완료 | `id`, `photoId`, `content`, `updatedAt` |
| `PHOTO_COMMENT_DELETED` | 댓글 soft delete 커밋 완료 | `id`, `photoId`, `deletedAt` |
| `SHARED_FOLDER_SUBSCRIPTION_REVOKED` | 나가기·공유 폴더 삭제 등으로 활성 멤버십 상실 | `sharedFolderId`, `reason` |

```json
{
  "type": "PHOTO_COMMENT_CREATED",
  "sharedFolderId": "b8a5f612-25d7-4ec3-9d1d-59684de40664",
  "eventId": "722359b4-b560-4d96-8907-3a61164030e2",
  "occurredAt": "2026-07-03T10:15:30Z",
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

클라이언트는 `eventId`로 중복 이벤트를 제거한다. 재연결 후에는 CHAT-01을 다시 조회해 누락 구간을 보정하며, 실시간 이벤트의 영구 replay는 1차 범위에서 지원하지 않는다.

### Fail Event Ⓧ

| code | 조건 | 연결 처리 |
|---|---|---|
| `UNAUTHORIZED` | Token 누락·만료·검증 실패 | 연결 종료 |
| `INVALID_EVENT` | JSON 또는 필수 필드 오류 | 오류 이벤트 후 연결 유지 |
| `SHARED_FOLDER_NOT_FOUND` | 공유 폴더 또는 활성 멤버십이 없음 | 해당 구독 거절, 연결 유지 |
| `TOO_MANY_SUBSCRIPTIONS` | 연결당 20개를 초과해 구독 요청 | 해당 구독 거절, 연결 유지 |

### Close Code

| Close Code | 의미 | 클라이언트 처리 |
|---:|---|---|
| `1000` | 정상 종료 | 필요할 때 다시 연결 |
| `1011` | 서버 내부 오류 | 지수 백오프로 다시 연결 |
| `4400` | 반복적인 잘못된 이벤트 | 요청 형식 수정 후 다시 연결 |
| `4401` | Access Token 누락·만료·검증 실패 | 토큰 갱신 또는 재로그인 후 다시 연결 |
| `4408` | heartbeat 응답 시간 초과 | 네트워크 확인 후 다시 연결 |

멤버십을 잃으면 해당 공유 폴더 구독만 해제하고 `SHARED_FOLDER_SUBSCRIPTION_REVOKED`를 보낸다. 다른 유효한 구독이 있을 수 있으므로 연결 전체를 종료하지 않는다.
