# 프로필·기기 API 명세

## 1. Notion DB 등록 정보

프로필과 기기 태그는 사용자 생명주기에 속하므로 `사용자·기기` index로 분류한다.

| ID | index | 이름 | HTTP Method | API Path | 상태 | 구현 여부 | 연동 o/x |
|---|---|---|---|---|---|---|---|
| USER-01 | 사용자·기기 | 내 프로필 조회 | GET | `/api/v1/users/me` | 시작 전 | false | false |
| USER-02 | 사용자·기기 | 내 프로필 수정 | PATCH | `/api/v1/users/me` | 시작 전 | false | false |
| USER-03 | 사용자·기기 | 사용자 탈퇴 | DELETE | `/api/v1/users/me` | 시작 전 | false | false |
| DEVICE-01 | 사용자·기기 | 내 기기 목록 조회 | GET | `/api/v1/users/me/devices` | 시작 전 | false | false |
| DEVICE-02 | 사용자·기기 | 내 기기 등록 | POST | `/api/v1/users/me/devices` | 시작 전 | false | false |
| DEVICE-03 | 사용자·기기 | 내 기기 수정 | PATCH | `/api/v1/users/me/devices/{deviceId}` | 시작 전 | false | false |
| DEVICE-04 | 사용자·기기 | 내 기기 삭제 | DELETE | `/api/v1/users/me/devices/{deviceId}` | 시작 전 | false | false |

모든 API는 Bearer 인증이 필요하며 공통 응답과 오류는 [01-common-spec.md](01-common-spec.md)를 따른다. 기기는 사용자의 주 사용 촬영 기기를 나타내는 표시용 태그이며 별도 유형은 두지 않는다.

## 2. USER-01 내 프로필 조회

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `Authorization` | String | O | Bearer Access Token |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "USER_PROFILE_FOUND",
  "message": "내 프로필을 조회했습니다.",
  "data": {
    "id": "018f0c3e-2c77-7d72-a37e-2f5666f25d32",
    "displayName": "집집이",
    "createdAt": "2026-07-03T10:15:30Z"
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `USER_NOT_FOUND` | 사용자가 없거나 탈퇴 상태임 |

## 3. USER-02 내 프로필 수정

### Request

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 | 예시 |
|---|---|---:|---|---|---|
| `displayName` | String | O | trim 후 1~50자 | 변경할 표시 이름 | `새 집집이` |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "USER_PROFILE_UPDATED",
  "message": "내 프로필을 수정했습니다.",
  "data": {
    "id": "018f0c3e-2c77-7d72-a37e-2f5666f25d32",
    "displayName": "새 집집이",
    "updatedAt": "2026-07-03T11:00:00Z"
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_DISPLAY_NAME` | 이름이 공백이거나 50자를 초과함 |
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `USER_NOT_FOUND` | 활성 사용자를 찾을 수 없음 |

## 4. USER-03 사용자 탈퇴

사용자의 `deletedAt`을 기록하고 `displayName`은 `탈퇴한 사용자`로 갱신한다. 모든 활성 Refresh Token을 폐기하고, 활성 기기는 soft delete하며, 사용자의 사진 좋아요는 물리 삭제한다.
방장으로 만든 공유 그룹은 soft delete하고, `MEMBER`로 참여 중인 멤버십은 물리 삭제한다. soft delete된 공유 그룹의 `HOST` 멤버십은 공유 그룹 물리 삭제 시 FK cascade로 정리한다.
탈퇴한 사용자가 기존에 생성·작성·업로드한 공유 콘텐츠는 즉시 삭제하지 않고 작성자 표시만 `탈퇴한 사용자`로 대체한다. 같은 Apple 계정으로 재가입하면 사용자 정보만 복구하며 과거 멤버십은 자동 복구하지 않는다.

### Request

요청 본문은 없다.

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "USER_WITHDRAWN",
  "message": "사용자 탈퇴가 완료되었습니다.",
  "data": null
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `USER_NOT_FOUND` | 활성 사용자를 찾을 수 없음 |

## 5. DEVICE-01 내 기기 목록 조회

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
  "code": "DEVICE_LIST_FOUND",
  "message": "내 기기 목록을 조회했습니다.",
  "data": {
    "items": [
      {
        "id": "2ec3f31f-22bd-46db-b255-43cab3da6c40",
        "name": "iPhone 16 Pro",
        "createdAt": "2026-07-03T10:15:30Z"
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

## 6. DEVICE-02 내 기기 등록

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Idempotency-Key` | UUID String | O | 기기 등록 재시도 식별자 | `54cf8d7e-a23e-4e76-90f7-603f122b1507` |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 | 예시 |
|---|---|---:|---|---|---|
| `name` | String | O | trim 후 1~100자 | 표시용 기기명 | `iPhone 16 Pro` |

### Success Response ✓

#### HTTP Status Code: `201 Created`

```json
{
  "status": 201,
  "code": "DEVICE_CREATED",
  "message": "기기를 등록했습니다.",
  "data": {
    "id": "2ec3f31f-22bd-46db-b255-43cab3da6c40",
    "name": "iPhone 16 Pro",
    "createdAt": "2026-07-03T10:15:30Z"
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_DEVICE_NAME` | 기기명이 공백이거나 100자를 초과함 |
| 409 | `DEVICE_ALREADY_EXISTS` | 같은 사용자에게 정규화된 이름이 같은 활성 기기가 있음 |

## 7. DEVICE-03 내 기기 수정

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `deviceId` | UUID | O | 수정할 내 기기 식별자 |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---:|---|---|
| `name` | String | O | trim 후 1~100자 | 새 기기명 |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "DEVICE_UPDATED",
  "message": "기기를 수정했습니다.",
  "data": {
    "id": "2ec3f31f-22bd-46db-b255-43cab3da6c40",
    "name": "iPhone 16 Pro",
    "createdAt": "2026-07-03T10:15:30Z",
    "updatedAt": "2026-07-03T12:00:00Z"
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_DEVICE_NAME` | 기기명 제약 위반 |
| 404 | `DEVICE_NOT_FOUND` | 내 활성 기기를 찾을 수 없음 |
| 409 | `DEVICE_ALREADY_EXISTS` | 변경 결과 활성 기기 중복 발생 |

## 8. DEVICE-04 내 기기 삭제

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `deviceId` | UUID | O | 삭제할 내 기기 식별자 |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "DEVICE_DELETED",
  "message": "기기를 삭제했습니다.",
  "data": null
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 404 | `DEVICE_NOT_FOUND` | 내 활성 기기를 찾을 수 없음 |
