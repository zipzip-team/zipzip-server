# 앱 시작·인증 API 명세

## 1. Notion DB 등록 정보

| ID | index | 이름 | HTTP Method | API Path | 상태 | 구현 여부 | 연동 o/x |
|---|---|---|---|---|---|---|---|
| AUTH-01 | 인증 | Apple 로그인 | POST | `/api/v1/auth/apple` | 시작 전 | false | false |
| AUTH-02 | 인증 | 토큰 갱신 | POST | `/api/v1/auth/refresh` | 시작 전 | false | false |
| AUTH-03 | 인증 | 로그아웃 | POST | `/api/v1/auth/logout` | 시작 전 | false | false |

공통 응답 형식과 공통 오류는 [01-common-spec.md](01-common-spec.md)를 따른다. Access Token과 Refresh Token의 유효 시간은 서버 보안 설정에서 확정하며 응답의 `expiresIn`은 초 단위다.

## 2. AUTH-01 Apple 로그인

Apple이 전달한 identity token을 검증하고 사용자를 생성·복구하거나 기존 사용자로 로그인한다. Apple이 이름을 제공하지 않는 최초 가입에서는 `displayName`이 필수다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Idempotency-Key` | UUID String | O | 로그인 재시도 식별자 | `54cf8d7e-a23e-4e76-90f7-603f122b1507` |
| `Content-Type` | String | O | 요청 본문 형식 | `application/json` |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 | 예시 |
|---|---|---:|---|---|---|
| `identityToken` | String | O | 유효한 Apple identity token | Apple 사용자 식별 검증에 사용한다. | `eyJraWQiOi...` |
| `authorizationCode` | String | O | 유효한 일회성 코드 | Apple 서버 검증 및 보안 감사에 사용한다. | `c1a2b3...` |
| `displayName` | String | 조건부 | trim 후 1~50자 | 최초 가입에서 Apple 이름을 얻지 못한 경우 필수다. | `집집이` |

```json
{
  "identityToken": "eyJraWQiOi...",
  "authorizationCode": "c1a2b3...",
  "displayName": "집집이"
}
```

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "AUTH_LOGIN_SUCCESS",
  "message": "로그인에 성공했습니다.",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "d4f3...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "isNewUser": true,
    "isRestoredUser": false,
    "user": {
      "id": "018f0c3e-2c77-7d72-a37e-2f5666f25d32",
      "displayName": "집집이"
    }
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `DISPLAY_NAME_REQUIRED` | 최초 가입이며 Apple 이름과 요청 이름이 모두 없음 |
| 400 | `INVALID_DISPLAY_NAME` | 이름이 공백이거나 50자를 초과함 |
| 401 | `INVALID_APPLE_TOKEN` | identity token의 서명, issuer, audience 또는 만료 검증 실패 |
| 401 | `INVALID_APPLE_AUTHORIZATION_CODE` | authorization code 검증 실패 |

## 3. AUTH-02 토큰 갱신

Refresh Token을 회전한다. 기존 토큰은 즉시 폐기하고 같은 token family의 새 토큰을 발급한다. 폐기된 토큰이 다시 제출되면 재사용 공격으로 간주해 해당 family 전체를 폐기한다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Idempotency-Key` | UUID String | O | 토큰 회전 재시도 식별자 | `54cf8d7e-a23e-4e76-90f7-603f122b1507` |
| `Content-Type` | String | O | 요청 본문 형식 | `application/json` |

#### Body

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `refreshToken` | String | O | 서버가 발급한 Refresh Token 원문 | `d4f3...` |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "AUTH_TOKEN_REFRESHED",
  "message": "토큰을 갱신했습니다.",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "f71a...",
    "tokenType": "Bearer",
    "expiresIn": 3600
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 401 | `INVALID_REFRESH_TOKEN` | 토큰이 없거나 해시가 일치하지 않음 |
| 401 | `REFRESH_TOKEN_EXPIRED` | Refresh Token 만료 |
| 401 | `REFRESH_TOKEN_REUSE_DETECTED` | 이미 폐기·회전된 토큰 재사용 감지 |
| 404 | `USER_NOT_FOUND` | 토큰 소유 사용자가 존재하지 않거나 탈퇴 상태임 |

## 4. AUTH-03 로그아웃

현재 로그인 세션의 Refresh Token을 폐기한다. 다른 기기의 token family는 유지한다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Authorization` | String | O | Bearer Access Token | `Bearer eyJhbGciOi...` |
| `Content-Type` | String | O | 요청 본문 형식 | `application/json` |

#### Body

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `refreshToken` | String | O | 현재 세션의 Refresh Token | `f71a...` |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "AUTH_LOGOUT_SUCCESS",
  "message": "로그아웃했습니다.",
  "data": null
}
```

이미 폐기된 현재 사용자 소유 토큰으로 다시 요청해도 같은 성공 응답을 반환한다.

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 401 | `UNAUTHORIZED` | Access Token 누락 또는 검증 실패 |
| 401 | `INVALID_REFRESH_TOKEN` | 다른 사용자 소유이거나 존재하지 않는 Refresh Token |
