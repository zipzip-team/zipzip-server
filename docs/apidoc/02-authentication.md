# 앱 시작·인증 API 명세

## 1. Notion DB 등록 정보

| ID | index | 이름 | HTTP Method | API Path | 상태 | 구현 여부 | 연동 o/x |
|---|---|---|---|---|---|---|---|
| AUTH-01 | 인증 | Apple 로그인 | POST | `/api/v1/auth/apple` | 완료 | true | false |
| AUTH-02 | 인증 | 토큰 갱신 | POST | `/api/v1/auth/refresh` | 완료 | true | false |
| AUTH-03 | 인증 | 로그아웃 | POST | `/api/v1/auth/logout` | 완료 | true | false |
| AUTH-DEV-01 | 인증 | 개발용 토큰 발급 | POST | `/api/v1/dev/auth/tokens` | 완료 | true | false |
| AUTH-DEV-02 | 인증 | 개발용 사용자 삭제 | DELETE | `/api/v1/dev/auth/users/{testUserKey}` | 완료 | true | false |

공통 응답 형식과 공통 오류는 [01-common-spec.md](01-common-spec.md)를 따른다. Access Token과 Refresh Token의 유효 시간은 서버 보안 설정에서 확정하며 응답의 `expiresIn`은 초 단위다.

## 2. AUTH-01 Apple 로그인

Apple 로그인 응답의 `identityToken`과 `authorizationCode`를 서버에서 검증하고 사용자를 생성·복구하거나 기존 사용자로 로그인한다. `nonce`는 Apple 로그인 요청을 만들 때 생성한 원문을 전달하며, `identityToken`의 nonce claim과 정확히 일치해야 한다. 신규 가입 또는 탈퇴 사용자 복구에서는 `displayName`이 필수다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Content-Type` | String | O | 요청 본문 형식 | `application/json` |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 | 예시 |
|---|---|---:|---|---|---|
| `identityToken` | JWT String | O | Apple이 발급한 유효한 identity token | iOS `ASAuthorizationAppleIDCredential.identityToken`에서 받은 값이다. Apple 사용자 식별과 서명·issuer·audience·만료·nonce 검증에 사용한다. | `eyJraWQiOi...` |
| `authorizationCode` | String | O | 유효한 일회성 코드 | iOS `ASAuthorizationAppleIDCredential.authorizationCode`에서 받은 값이다. 서버가 Apple 토큰 엔드포인트에 검증 요청할 때 사용하므로 로그인마다 새 값을 전달한다. | `c1a2b3...` |
| `nonce` | String | O | identity token의 nonce claim과 정확히 일치 | iOS에서 Apple 로그인 요청을 만들 때 생성한 nonce 원문이다. 해시값이나 다른 로그인 시도의 nonce를 보내면 안 된다. | `8f5d29f2-54e9-4da1-8a06-08e394af897e` |
| `displayName` | String | 조건부 | trim 후 1~50자 | 신규 가입 또는 탈퇴 사용자 복구 시 필수다. 기존 활성 사용자는 생략할 수 있다. | `집집이` |

```json
{
  "identityToken": "eyJraWQiOi...",
  "authorizationCode": "c1a2b3...",
  "nonce": "8f5d29f2-54e9-4da1-8a06-08e394af897e",
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
    "expiresIn": 1800,
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
| 400 | `INVALID_REQUEST` | 필수 요청 본문 값 누락 또는 형식 오류 |
| 400 | `DISPLAY_NAME_REQUIRED` | 신규 가입 또는 탈퇴 사용자 복구에 필요한 표시 이름이 없음·공백임 |
| 400 | `INVALID_DISPLAY_NAME` | 이름이 공백이거나 50자를 초과함 |
| 401 | `INVALID_APPLE_TOKEN` | identity token의 서명, issuer, audience 또는 만료 검증 실패 |
| 401 | `INVALID_APPLE_AUTHORIZATION_CODE` | authorization code 검증 실패 |

## 3. AUTH-DEV-01 개발용 토큰 발급

Apple 로그인을 진행할 수 없는 클라이언트 개발·테스트를 위해 Access Token과 Refresh Token을 발급한다. 이 API는 `dev` Spring 프로필에서만 등록되며, 운영 환경에는 컨트롤러와 엔드포인트가 존재하지 않는다. 개발 서버에서만 사용하고 발급한 토큰은 개발 환경 외부에 보관하거나 사용하면 안 된다.

같은 `testUserKey`로 반복 요청하면 동일한 개발용 사용자를 재사용하되, 매번 새 Access Token과 Refresh Token을 발급한다. 탈퇴 처리된 개발용 사용자는 요청의 `displayName`으로 복구한다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---:|---:|---|---|
| `Content-Type` | String | O | 요청 본문 형식 | `application/json` |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 | 예시 |
|---|---|---:|---|---|---|
| `testUserKey` | String | O | 영문 대소문자·숫자·`.`, `_`, `-`, 최대 50자 | 개발용 사용자 식별 키 | `ios-tester-1` |
| `displayName` | String | O | trim 후 1~50자 | 신규 생성 또는 탈퇴 사용자 복구 시 사용할 표시 이름 | `iOS 테스트 사용자` |

```json
{
  "testUserKey": "ios-tester-1",
  "displayName": "iOS 테스트 사용자"
}
```

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "AUTH_DEVELOPMENT_TOKEN_ISSUED",
  "message": "개발용 토큰을 발급했습니다.",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJhbGciOi...",
    "tokenType": "Bearer",
    "expiresIn": 1800,
    "isNewUser": true,
    "isRestoredUser": false,
    "user": {
      "id": "018f0c3e-2c77-7d72-a37e-2f5666f25d32",
      "displayName": "iOS 테스트 사용자"
    }
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_REQUEST` | 요청 본문 값 누락, `testUserKey` 형식 오류 또는 50자 초과 |
| 400 | `DISPLAY_NAME_REQUIRED` | `displayName`이 공백임 |
| 400 | `INVALID_DISPLAY_NAME` | `displayName`이 50자를 초과함 |

## 4. AUTH-DEV-02 개발용 사용자 삭제

개발용 토큰 발급 API로 생성한 활성 개발용 사용자를 탈퇴 처리한다. 탈퇴 과정에서 활성 Refresh Token을 폐기하고, 사진 좋아요·일반 멤버십·사용자가 만든 활성 공유 그룹 등 일반 사용자 탈퇴와 동일한 정리를 수행한다. 이 API도 `dev` Spring 프로필에서만 등록된다.

### Request

#### Path Variable

| 필드 | 타입 | 필수 | 제약 | 설명 | 예시 |
|---|---|---:|---|---|---|
| `testUserKey` | String | O | 토큰 발급 요청에 사용한 키 | 삭제할 개발용 사용자 식별 키 | `ios-tester-1` |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "AUTH_DEVELOPMENT_USER_DELETED",
  "message": "개발용 사용자를 삭제했습니다.",
  "data": null
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 404 | `USER_NOT_FOUND` | 해당 키의 활성 개발용 사용자가 없거나 이미 삭제됨 |

## 5. AUTH-02 토큰 갱신

Refresh Token을 회전한다. 기존 토큰은 즉시 폐기하고 같은 token family의 새 토큰을 발급한다. 폐기된 토큰이 다시 제출되면 재사용 공격으로 간주해 해당 family 전체를 폐기한다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Idempotency-Key` | UUID String | O | 클라이언트가 생성하는 토큰 회전 재시도 식별자. 네트워크 재시도에는 같은 Refresh Token과 같은 UUID를 사용한다. | `54cf8d7e-a23e-4e76-90f7-603f122b1507` |
| `Content-Type` | String | O | 요청 본문 형식 | `application/json` |

#### Body

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `refreshToken` | String | O | 서버가 발급한 Refresh Token 원문 | `d4f3...` |

### Success Response ✓

#### HTTP Status Code: `200 OK`

같은 `Idempotency-Key`와 같은 요청을 재시도해 저장된 성공 응답을 받은 경우에만 `Idempotency-Replayed: true` 응답 헤더가 포함된다.

```json
{
  "status": 200,
  "code": "AUTH_TOKEN_REFRESHED",
  "message": "토큰을 갱신했습니다.",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "f71a...",
    "tokenType": "Bearer",
    "expiresIn": 1800
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_REQUEST` | `Idempotency-Key` 누락·UUID 형식 오류 또는 Refresh Token 요청 값 오류 |
| 401 | `INVALID_REFRESH_TOKEN` | 토큰이 없거나 해시가 일치하지 않음 |
| 401 | `REFRESH_TOKEN_EXPIRED` | Refresh Token 만료 |
| 401 | `REFRESH_TOKEN_REUSE_DETECTED` | 이미 폐기·회전된 토큰 재사용 감지 |
| 404 | `USER_NOT_FOUND` | 토큰 소유 사용자가 존재하지 않거나 탈퇴 상태임 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 `Idempotency-Key`를 다른 Refresh Token 요청에 재사용함 |
| 409 | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 요청이 아직 처리 중임 |

## 6. AUTH-03 로그아웃

현재 로그인 세션의 Refresh Token을 폐기한다. 다른 기기의 token family는 유지한다. 이미 발급된 Access Token은
블랙리스트로 즉시 무효화하지 않으며, 설정된 만료 시각까지 사용할 수 있다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Authorization` | String | O | 현재 세션의 Bearer Access Token. `Bearer ` 접두사를 포함한다. | `Bearer eyJhbGciOi...` |
| `Content-Type` | String | O | 요청 본문 형식 | `application/json` |

#### Body

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `refreshToken` | JWT String | O | Authorization 헤더의 Access Token과 같은 사용자에게 발급된 현재 세션의 Refresh Token. 이 토큰만 폐기하며 다른 기기 토큰은 유지한다. | `eyJhbGciOiJIUzI1NiJ9...` |

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
| 400 | `INVALID_REQUEST` | Refresh Token 요청 값 누락 또는 형식 오류 |
| 401 | `UNAUTHORIZED` | Access Token 누락 또는 검증 실패 |
| 401 | `INVALID_REFRESH_TOKEN` | 다른 사용자 소유이거나 존재하지 않는 Refresh Token |
