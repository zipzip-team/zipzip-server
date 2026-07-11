# 공유 그룹 목록·관리 API 명세

## 1. Notion DB 등록 정보

| ID | index | 이름 | HTTP Method | API Path | 상태 | 구현 여부 | 연동 o/x |
|---|---|---|---|---|---|---|---|
| GROUP-01 | 공유 그룹 | 내 공유 그룹 목록 조회 | GET | `/api/v1/shared-groups` | 완료 | true | false |
| GROUP-02 | 공유 그룹 | 공유 그룹 생성 | POST | `/api/v1/shared-groups` | 완료 | true | false |
| GROUP-03 | 공유 그룹 | 공유 그룹 상세 조회 | GET | `/api/v1/shared-groups/{sharedGroupId}` | 완료 | true | false |
| GROUP-04 | 공유 그룹 | 공유 그룹 이름 수정 | PATCH | `/api/v1/shared-groups/{sharedGroupId}` | 완료 | true | false |
| GROUP-05 | 공유 그룹 | 공유 그룹 삭제 | DELETE | `/api/v1/shared-groups/{sharedGroupId}` | 완료 | true | false |
| GROUP-06 | 공유 그룹 | 공유 그룹 멤버 목록 조회 | GET | `/api/v1/shared-groups/{sharedGroupId}/members` | 완료 | true | false |

모든 API는 `Authorization: Bearer <accessToken>` 헤더가 필요하다. `accessToken`에는 로그인 또는 토큰 갱신 응답에서 받은 값을 사용한다. 조회는 활성 멤버십을 요구하고, 공유 그룹 정보 수정·삭제는 활성 `HOST`만 가능하다. 공통 응답과 오류는 [01-common-spec.md](01-common-spec.md)를 따른다.

## 2. GROUP-01 내 공유 그룹 목록 조회

활성 멤버십의 참여일시 내림차순으로 조회하며 동일 시각은 공유 그룹 ID로 순서를 고정한다.

### Request

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
  "code": "SHARED_GROUP_LIST_FOUND",
  "message": "내 공유 그룹 목록을 조회했습니다.",
  "data": {
    "items": [
      {
        "id": "b8a5f612-25d7-4ec3-9d1d-59684de40664",
        "name": "우리 집",
        "myRole": "HOST",
        "memberCount": 4,
        "sharedAlbumCount": 3,
        "photoCount": 128,
        "joinedAt": "2026-07-03T10:15:30Z",
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

## 3. GROUP-02 공유 그룹 생성

초대 코드 예약 원장, 공유 그룹, 생성자의 `HOST` 멤버십을 하나의 트랜잭션에서 생성한다. 그룹 채팅 메시지는 `shared_group_chat_message`로 별도 저장한다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Authorization` | String | O | 현재 세션의 Access Token. `Bearer ` 접두사를 포함한다. | `Bearer eyJhbGciOiJIUzI1NiJ9...` |
| `Idempotency-Key` | UUID String | O | 클라이언트가 생성하는 공유 그룹 생성 재시도 식별자. 같은 논리적 요청 재시도에는 같은 UUID와 동일한 요청 본문을 사용한다. | `54cf8d7e-a23e-4e76-90f7-603f122b1507` |
| `Content-Type` | String | O | 요청 본문 형식 | `application/json` |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 | 예시 |
|---|---|---:|---|---|---|
| `name` | String | O | 앞뒤 공백 제거 후 1~100자 | 공유 그룹 이름 | `우리 집` |

### Success Response ✓

#### HTTP Status Code: `201 Created`

같은 `Idempotency-Key`와 같은 요청을 재시도해 저장된 성공 응답을 받은 경우에만 `Idempotency-Replayed: true` 응답 헤더가 포함된다.

```json
{
  "status": 201,
  "code": "SHARED_GROUP_CREATED",
  "message": "공유 그룹을 생성했습니다.",
  "data": {
    "id": "b8a5f612-25d7-4ec3-9d1d-59684de40664",
    "name": "우리 집",
    "inviteCode": "ZZ7K9P2Q",
    "myRole": "HOST",
    "createdBy": {
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
| 400 | `INVALID_SHARED_GROUP_NAME` | 이름이 공백이거나 100자를 초과함 |
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 `Idempotency-Key`를 다른 생성 요청에 재사용함 |
| 409 | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 요청이 아직 처리 중임 |
| 500 | `INVITE_CODE_GENERATION_FAILED` | 제한된 재시도 안에 고유 초대 코드 예약 실패 |

## 4. GROUP-03 공유 그룹 상세 조회

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedGroupId` | UUID | O | 조회할 공유 그룹 식별자 |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "SHARED_GROUP_FOUND",
  "message": "공유 그룹을 조회했습니다.",
  "data": {
    "id": "b8a5f612-25d7-4ec3-9d1d-59684de40664",
    "name": "우리 집",
    "myRole": "MEMBER",
    "createdBy": {
      "userId": "018f0c3e-2c77-7d72-a37e-2f5666f25d32",
      "displayName": "집집이"
    },
    "memberCount": 4,
    "sharedAlbumCount": 3,
    "photoCount": 128,
    "createdAt": "2026-07-03T10:15:30Z",
    "updatedAt": "2026-07-03T10:15:30Z"
  }
}
```

`createdBy`는 최초 생성자이고 `myRole`과 멤버 목록의 `HOST`는 현재 권한을 나타낸다. 초대 코드는 전용 API에서만 반환한다.

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 404 | `SHARED_GROUP_NOT_FOUND` | 활성 공유 그룹이 없거나 요청 사용자에게 활성 멤버십이 없음 |

## 5. GROUP-04 공유 그룹 이름 수정

활성 `HOST`만 수정할 수 있다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedGroupId` | UUID | O | 수정할 공유 그룹 식별자 |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---:|---|---|
| `name` | String | O | 앞뒤 공백 제거 후 1~100자 | 새 공유 그룹 이름 |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "SHARED_GROUP_UPDATED",
  "message": "공유 그룹 이름을 수정했습니다.",
  "data": {
    "id": "b8a5f612-25d7-4ec3-9d1d-59684de40664",
    "name": "여름 여행",
    "updatedAt": "2026-07-03T12:00:00Z"
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_REQUEST` | 요청 본문 누락·형식 오류 또는 name 검증 오류 |
| 400 | `INVALID_SHARED_GROUP_NAME` | 이름 제약 위반 |
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 403 | `ONLY_HOST_CAN_UPDATE_SHARED_GROUP` | 활성 멤버지만 `HOST`가 아님 |
| 404 | `SHARED_GROUP_NOT_FOUND` | 공유 그룹 또는 활성 멤버십이 없음 |

## 6. GROUP-05 공유 그룹 삭제

방장만 실행할 수 있다. 요청 시 공유 그룹·하위 공유집(앨범)·사진을 같은 시각에 soft delete해 접근을 즉시 차단한다. 사진-공유집(앨범) 매핑은 30일 물리 정리 전까지 유지하며, 배치는 원본·썸네일 Object Storage 객체를 먼저 삭제한다. 모든 사진 정리에 성공한 경우에만 사진 관련 행, 공유 그룹과 초대 코드 예약을 순서대로 물리 삭제한다. 스토리지 삭제에 실패하면 공유 그룹과 초대 코드 예약을 유지해 다음 배치에서 재시도한다. 사용자 복구 API는 제공하지 않는다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedGroupId` | UUID | O | 삭제할 공유 그룹 식별자 |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "SHARED_GROUP_DELETED",
  "message": "공유 그룹을 삭제했습니다.",
  "data": null
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 403 | `ONLY_HOST_CAN_DELETE_SHARED_GROUP` | 활성 멤버지만 `HOST`가 아님 |
| 404 | `SHARED_GROUP_NOT_FOUND` | 활성 공유 그룹 또는 활성 멤버십이 없음 |

## 7. GROUP-06 공유 그룹 멤버 목록 조회

활성 멤버십의 참여일시 오름차순으로 조회한다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedGroupId` | UUID | O | 공유 그룹 식별자 |

#### Query

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---:|---|---|
| `cursor` | String | X | null | 이전 응답의 불투명 cursor |
| `size` | Integer | X | 50 | 1~100 |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "SHARED_GROUP_MEMBER_LIST_FOUND",
  "message": "공유 그룹 멤버 목록을 조회했습니다.",
  "data": {
    "items": [
      {
        "userId": "018f0c3e-2c77-7d72-a37e-2f5666f25d32",
        "displayName": "집집이",
        "role": "HOST",
        "isMe": true,
        "joinedAt": "2026-07-03T10:15:30Z"
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
| 404 | `SHARED_GROUP_NOT_FOUND` | 공유 그룹 또는 활성 멤버십이 없음 |
