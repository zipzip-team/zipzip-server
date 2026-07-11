# 공유 그룹 초대·참여 API 명세

## 1. Notion DB 등록 정보

| ID | index | 이름 | HTTP Method | API Path | 상태 | 구현 여부 | 연동 o/x |
|---|---|---|---|---|---|---|---|
| INVITE-01 | 공유 그룹 | 초대 코드 조회 | GET | `/api/v1/shared-groups/{sharedGroupId}/invite-code` | 완료 | true | false |
| INVITE-02 | 공유 그룹 | 초대 코드로 참여 | POST | `/api/v1/shared-groups/join` | 완료 | true | false |
| INVITE-03 | 공유 그룹 | 공유 그룹 나가기 | DELETE | `/api/v1/shared-groups/{sharedGroupId}/members/me` | 완료 | true | false |

모든 API는 `Authorization: Bearer <accessToken>` 헤더가 필요하며, `accessToken`에는 로그인 또는 토큰 갱신 응답에서 받은 값을 사용한다. 공통 응답과 오류는 [01-common-spec.md](01-common-spec.md)를 따른다. 초대 코드는 공유 그룹 생성 시 고정되며, 공유 그룹이 활성 또는 soft delete 상태인 동안 만료·재발급·재사용하지 않는다. 공유 그룹 물리 삭제 시 초대 코드 예약도 정리되어 이후 재사용될 수 있다.

## 2. INVITE-01 초대 코드 조회

활성 방장과 멤버 모두 조회할 수 있다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedGroupId` | UUID | O | 초대 코드를 조회할 공유 그룹 식별자 |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "INVITE_CODE_FOUND",
  "message": "초대 코드를 조회했습니다.",
  "data": {
    "sharedGroupId": "b8a5f612-25d7-4ec3-9d1d-59684de40664",
    "inviteCode": "ZZ7K9P2Q"
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `SHARED_GROUP_NOT_FOUND` | 활성 공유 그룹 또는 요청 사용자의 활성 멤버십이 없음 |

## 3. INVITE-02 초대 코드로 참여

초대 코드와 활성 공유 그룹을 확인하고 `MEMBER` 멤버십을 생성한다. 과거에 나간 사용자도 새 멤버십으로 재참여한다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Authorization` | String | O | 현재 세션의 Access Token. `Bearer ` 접두사를 포함한다. | `Bearer eyJhbGciOiJIUzI1NiJ9...` |
| `Idempotency-Key` | UUID String | O | 클라이언트가 생성하는 참여 재시도 식별자. 같은 논리적 요청 재시도에는 같은 UUID와 동일한 요청 본문을 사용한다. | `54cf8d7e-a23e-4e76-90f7-603f122b1507` |
| `Content-Type` | String | O | 요청 본문 형식 | `application/json` |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 | 예시 |
|---|---|---:|---|---|---|
| `inviteCode` | String | O | 앞뒤 공백 제거 후 1~64자 | 참여할 활성 공유 그룹의 초대 코드 | `ZZ7K9P2Q` |

### Success Response ✓

#### HTTP Status Code: `201 Created`

같은 `Idempotency-Key`와 같은 요청을 재시도해 저장된 성공 응답을 받은 경우에만 `Idempotency-Replayed: true` 응답 헤더가 포함된다.

```json
{
  "status": 201,
  "code": "SHARED_GROUP_JOINED",
  "message": "공유 그룹에 참여했습니다.",
  "data": {
    "sharedGroupId": "b8a5f612-25d7-4ec3-9d1d-59684de40664",
    "name": "우리 집",
    "myRole": "MEMBER",
    "joinedAt": "2026-07-03T10:15:30Z"
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_REQUEST` | `Idempotency-Key` 누락·UUID 형식 오류, 요청 본문 형식 오류 또는 초대 코드가 공백·64자 초과임 |
| 400 | `INVALID_INVITE_CODE` | 유효한 활성 공유 그룹에 해당하지 않는 초대 코드임 |
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 409 | `ALREADY_JOINED_SHARED_GROUP` | 이미 활성 멤버십이 있음 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 `Idempotency-Key`를 다른 참여 요청에 재사용함 |
| 409 | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 요청이 아직 처리 중임 |

## 4. INVITE-03 공유 그룹 나가기

활성 `MEMBER` 멤버십 행을 즉시 물리 삭제한다. 기존 사진·좋아요·댓글은 유지하며, 나간 사용자는 유효한 초대 코드로 새 멤버십을 만들어 재참여할 수 있다. 방장은 나갈 수 없으며 공유 그룹 삭제만 가능하다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedGroupId` | UUID | O | 나갈 공유 그룹 식별자 |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "SHARED_GROUP_LEFT",
  "message": "공유 그룹에서 나갔습니다.",
  "data": null
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 403 | `HOST_CANNOT_LEAVE_SHARED_GROUP` | 요청 사용자가 활성 방장임 |
| 404 | `SHARED_GROUP_NOT_FOUND` | 활성 공유 그룹 또는 요청 사용자의 활성 멤버십이 없음 |
