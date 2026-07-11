# 프로필 API 명세

## 1. Notion DB 등록 정보

프로필은 사용자 생명주기에 속하므로 `사용자` index로 분류한다.

| ID | index | 이름 | HTTP Method | API Path | 상태 | 구현 여부 | 연동 o/x |
|---|---|---|---|---|---|---|---|
| USER-01 | 사용자 | 내 프로필 조회 | GET | `/api/v1/users/me` | 완료 | true | false |
| USER-02 | 사용자 | 내 프로필 수정 | PATCH | `/api/v1/users/me` | 완료 | true | false |
| USER-03 | 사용자 | 사용자 탈퇴 | DELETE | `/api/v1/users/me` | 완료 | true | false |

모든 API는 Bearer 인증이 필요하며 공통 응답과 오류는 [01-common-spec.md](01-common-spec.md)를 따른다.

## 2. USER-01 내 프로필 조회

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `Authorization` | String | O | 로그인 또는 토큰 갱신 응답에서 받은 현재 세션의 Access Token. `Bearer ` 접두사를 포함한다. 예: `Bearer eyJhbGciOiJIUzI1NiJ9...` |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "USER_PROFILE_FOUND",
  "message": "내 프로필을 조회했습니다.",
  "data": {
    "displayName": "집집이"
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

#### Header

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `Authorization` | String | O | 로그인 또는 토큰 갱신 응답에서 받은 현재 세션의 Access Token. `Bearer ` 접두사를 포함한다. 예: `Bearer eyJhbGciOiJIUzI1NiJ9...` |
| `Content-Type` | String | O | 요청 본문 형식. `application/json`을 전달한다. |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 | 예시 |
|---|---|---:|---|---|---|
| `displayName` | String | O | 앞뒤 공백 제거 후 1~50자 | 변경할 표시 이름. 공백만 있거나 50자를 초과하면 거부한다. | `새 집집이` |

```json
{
  "displayName": "새 집집이"
}
```

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "USER_PROFILE_UPDATED",
  "message": "내 프로필을 수정했습니다.",
  "data": {
    "displayName": "새 집집이"
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_REQUEST` | 요청 본문이 없거나 JSON 형식이 올바르지 않음 |
| 400 | `INVALID_DISPLAY_NAME` | 이름이 공백이거나 50자를 초과함 |
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `USER_NOT_FOUND` | 활성 사용자를 찾을 수 없음 |

## 4. USER-03 사용자 탈퇴

사용자의 `deletedAt`을 기록하고 `displayName`은 `탈퇴한 사용자`로 갱신한다. 모든 활성 Refresh Token을 폐기하고, 사용자의 사진 좋아요는 물리 삭제한다.
방장으로 만든 공유 그룹은 soft delete하고, `MEMBER`로 참여 중인 멤버십은 물리 삭제한다. soft delete된 공유 그룹의 `HOST` 멤버십은 공유 그룹 물리 삭제 시 FK cascade로 정리한다.
탈퇴한 사용자가 기존에 생성·작성·업로드한 공유 콘텐츠는 즉시 삭제하지 않고 작성자 표시만 `탈퇴한 사용자`로 대체한다. 같은 Apple 계정으로 재가입하면 사용자 정보만 복구하며 과거 멤버십은 자동 복구하지 않는다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `Authorization` | String | O | 탈퇴할 현재 계정의 Access Token. `Bearer ` 접두사를 포함한다. 예: `Bearer eyJhbGciOiJIUzI1NiJ9...` |

요청 본문은 없다. 탈퇴가 완료되면 현재 사용자의 모든 활성 Refresh Token이 폐기되므로, 이후 다시 이용하려면 Apple 로그인으로 새 인증 세션을 발급받아야 한다.

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
