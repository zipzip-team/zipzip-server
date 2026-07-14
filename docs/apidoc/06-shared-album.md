# 공유집(앨범) API 명세

## 1. Notion DB 등록 정보

공유집(앨범)은 공유 그룹 하위 리소스이며 물리 모델은 `shared_album`이다.
사진은 공유 그룹에 직접 속하지 않고, `shared_album_photo` 관계 테이블로 하나 이상의 공유집(앨범)에만 속한다(공유 위계는 공유 그룹 > 공유집(앨범) > 사진).
기존 사진을 공유집(앨범)에 추가·제거하는 API는 [07-photo-management.md](07-photo-management.md)의 PHOTO-07, PHOTO-08을 따른다.
`photoCount`는 활성 `shared_album_photo` 매핑과 활성 사진 기준으로 실시간 count한다.

> 엔드포인트별 백엔드 동작·iOS 연동 가이드는 [shared-album-photo-ios-integration-guide.md](shared-album-photo-ios-integration-guide.md) 참고.

| ID | index | 이름 | HTTP Method | API Path | 상태 | 구현 여부 | 연동 o/x |
|---|---|---|---|---|---|---|---|
| ALBUM-01 | 앨범 | 공유집(앨범) 목록 조회 | GET | `/api/v1/shared-groups/{sharedGroupId}/shared-albums` | 완료 | true | false |
| ALBUM-02 | 앨범 | 공유집(앨범) 생성 | POST | `/api/v1/shared-groups/{sharedGroupId}/shared-albums` | 완료 | true | false |
| ALBUM-03 | 앨범 | 공유집(앨범) 상세 조회 | GET | `/api/v1/shared-albums/{sharedAlbumId}` | 완료 | true | false |
| ALBUM-04 | 앨범 | 공유집(앨범) 이름 수정 | PATCH | `/api/v1/shared-albums/{sharedAlbumId}` | 완료 | true | false |
| ALBUM-05 | 앨범 | 공유집(앨범) 삭제 | DELETE | `/api/v1/shared-albums/{sharedAlbumId}` | 완료 | true | false |
| ALBUM-06 | 앨범 | 공유집(앨범) 일괄 삭제 | POST | `/api/v1/shared-albums/bulk-delete` | 완료 | true | false |

모든 API는 `Authorization: Bearer <accessToken>` 헤더와 대상 공유 그룹의 활성 멤버십을 요구한다. `accessToken`에는 로그인 또는 토큰 갱신 응답에서 받은 값을 사용한다.
방장과 멤버 모두 공유집(앨범)을 생성·수정·삭제할 수 있다(생성자·방장 여부와 무관).
공통 응답과 오류는 [01-common-spec.md](01-common-spec.md)를 따른다.

## 2. ALBUM-01 공유집(앨범) 목록 조회

생성일시 내림차순으로 조회하며 동일 시각은 공유집(앨범) ID로 순서를 고정한다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedGroupId` | UUID | O | 공유 그룹 식별자 |

#### Query

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---:|---|---|
| `cursor` | String | X | null | 이전 응답의 `nextCursor`를 수정하지 않고 그대로 전달하는 불투명 cursor |
| `size` | Integer | X | 20 | 생략 시 20. 1 미만은 1, 100 초과는 100으로 보정 |

### Success Response

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "SHARED_ALBUM_LIST_FOUND",
  "message": "공유집(앨범) 목록을 조회했습니다.",
  "data": {
    "items": [
      {
        "id": "59ce0d18-a53e-4197-9c3c-e82331adc097",
        "name": "제주도",
        "photoCount": 42,
        "createdBy": {
          "userId": "018f0c3e-2c77-7d72-a37e-2f5666f25d32",
          "displayName": "집집이"
        },
        "isCreator": true,
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
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `SHARED_GROUP_NOT_FOUND` | 공유 그룹 또는 활성 멤버십이 없음 |

## 3. ALBUM-02 공유집(앨범) 생성

사진이 없는 빈 공유집(앨범)도 생성할 수 있다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Authorization` | String | O | 현재 세션의 Access Token. `Bearer ` 접두사를 포함한다. | `Bearer eyJhbGciOiJIUzI1NiJ9...` |
| `Idempotency-Key` | UUID String | O | 클라이언트가 생성하는 앨범 생성 재시도 식별자. 같은 논리적 요청 재시도에는 같은 UUID와 동일한 요청 본문을 사용한다. | `54cf8d7e-a23e-4e76-90f7-603f122b1507` |
| `Content-Type` | String | O | 요청 본문 형식 | `application/json` |

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedGroupId` | UUID | O | 공유 그룹 식별자 |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 | 예시 |
|---|---|---:|---|---|---|
| `name` | String | O | 앞뒤 공백 제거 후 1~100자 | 공유집(앨범) 이름 | `제주도` |

같은 `Idempotency-Key`와 같은 요청을 재시도하면 최초 성공 응답을 다시 반환한다. 현재 이 API는 재전송 여부를 나타내는 별도 응답 헤더를 제공하지 않는다.

### Success Response

#### HTTP Status Code: `201 Created`

```json
{
  "status": 201,
  "code": "SHARED_ALBUM_CREATED",
  "message": "공유집(앨범)을 생성했습니다.",
  "data": {
    "id": "59ce0d18-a53e-4197-9c3c-e82331adc097",
    "sharedGroupId": "b8a5f612-25d7-4ec3-9d1d-59684de40664",
    "name": "제주도",
    "photoCount": 0,
    "createdBy": {
      "userId": "018f0c3e-2c77-7d72-a37e-2f5666f25d32",
      "displayName": "집집이"
    },
    "isCreator": true,
    "createdAt": "2026-07-03T10:15:30Z"
  }
}
```

### Fail Response

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_REQUEST` | `Idempotency-Key` 누락·UUID 형식 오류 또는 요청 본문 형식 오류 |
| 400 | `INVALID_SHARED_ALBUM_NAME` | 이름이 공백이거나 100자를 초과함 |
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `SHARED_GROUP_NOT_FOUND` | 공유 그룹 또는 활성 멤버십이 없음 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 `Idempotency-Key`를 다른 생성 요청에 재사용함 |
| 409 | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 요청이 아직 처리 중임 |

## 4. ALBUM-03 공유집(앨범) 상세 조회

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedAlbumId` | UUID | O | 공유집(앨범) 식별자 |

### Success Response

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "SHARED_ALBUM_FOUND",
  "message": "공유집(앨범)을 조회했습니다.",
  "data": {
    "id": "59ce0d18-a53e-4197-9c3c-e82331adc097",
    "sharedGroupId": "b8a5f612-25d7-4ec3-9d1d-59684de40664",
    "name": "제주도",
    "photoCount": 42,
    "createdBy": {
      "userId": "018f0c3e-2c77-7d72-a37e-2f5666f25d32",
      "displayName": "집집이"
    },
    "isCreator": true,
    "createdAt": "2026-07-03T10:15:30Z",
    "updatedAt": "2026-07-03T10:15:30Z"
  }
}
```

### Fail Response

| HTTP Status | code | 조건 |
|---:|---|---|
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `SHARED_ALBUM_NOT_FOUND` | 공유집(앨범)이 없거나 상위 공유 그룹의 활성 멤버십이 없음 |

## 5. ALBUM-04 공유집(앨범) 이름 수정

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedAlbumId` | UUID | O | 공유집(앨범) 식별자 |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---:|---|---|
| `name` | String | O | 앞뒤 공백 제거 후 1~100자 | 새 공유집(앨범) 이름 |

### Success Response

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "SHARED_ALBUM_UPDATED",
  "message": "공유집(앨범) 이름을 수정했습니다.",
  "data": {
    "id": "59ce0d18-a53e-4197-9c3c-e82331adc097",
    "name": "제주 여름",
    "updatedAt": "2026-07-03T12:00:00Z"
  }
}
```

### Fail Response

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_REQUEST` | 요청 본문 누락·형식 오류 또는 name 검증 오류 |
| 400 | `INVALID_SHARED_ALBUM_NAME` | 이름 제약 위반 |
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `SHARED_ALBUM_NOT_FOUND` | 공유집(앨범) 또는 활성 멤버십이 없음 |

## 6. ALBUM-05 공유집(앨범) 삭제

공유집(앨범)을 soft delete해 해당 공유집(앨범) 접근을 즉시 차단하고, 그 안의 `shared_album_photo` 매핑을 모두 물리 삭제한다.
매핑이 삭제된 사진 중 다른 활성 공유집(앨범)에도 속한 사진은 원본을 그대로 유지하고 계속 접근할 수 있다.
방금 삭제한 공유집(앨범)이 마지막 소속이었던 사진은 원본도 함께 soft delete되어 접근이 차단된다(사진은 공유 그룹에 직접 속하지 않고 공유집(앨범)을 통해서만 속하므로, 소속 공유집(앨범)이 모두 없어지면 사진도 함께 정리된다).
30일 뒤 `shared_album` 행을 물리 삭제한다. 함께 soft delete된 사진은 각자의 삭제 정책(Object Storage 객체 정리 후 물리 삭제)을 별도로 따른다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedAlbumId` | UUID | O | 공유집(앨범) 식별자 |

### Success Response

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "SHARED_ALBUM_DELETED",
  "message": "공유집(앨범)을 삭제했습니다.",
  "data": null
}
```

### Fail Response

| HTTP Status | code | 조건 |
|---:|---|---|
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `SHARED_ALBUM_NOT_FOUND` | 공유집(앨범) 또는 활성 멤버십이 없음 |

## 7. ALBUM-06 공유집(앨범) 일괄 삭제

여러 공유집(앨범)을 한 번에 삭제한다. 대상별 동작은 ALBUM-05와 동일하며(soft delete, `shared_album_photo` 매핑 물리 삭제, 마지막 소속을 잃는 사진 원본 soft delete), 대상 전체를 먼저 검증한 뒤 하나라도 존재하지 않거나 활성 멤버십이 없으면 어떤 공유집(앨범)도 삭제하지 않는다.
대상 공유집(앨범)이 서로 다른 공유 그룹에 속해도 무방하며, 각 대상은 자신이 속한 공유 그룹의 활성 멤버십만으로 독립적으로 검증한다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Authorization` | String | O | 현재 세션의 Access Token. `Bearer ` 접두사를 포함한다. | `Bearer eyJhbGciOiJIUzI1NiJ9...` |
| `Idempotency-Key` | UUID String | O | 클라이언트가 생성하는 일괄 삭제 재시도 식별자. 같은 논리적 요청 재시도에는 같은 UUID와 동일한 요청 본문을 사용한다. | `7a6e9c2e-3b7a-4c1a-9c3e-8b8f3a2b5f11` |
| `Content-Type` | String | O | 요청 본문 형식 | `application/json` |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---:|---|---|
| `sharedAlbumIds` | UUID[] | O | `null` 원소 없이 중복 없이 1~100개 | 삭제할 공유집(앨범) 식별자 목록 |

같은 `Idempotency-Key`와 같은 요청을 재시도하면 최초 성공 응답을 다시 반환한다.

### Success Response

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "SHARED_ALBUMS_DELETED",
  "message": "공유집(앨범)을 일괄 삭제했습니다.",
  "data": {
    "deletedAlbumCount": 2,
    "deletedPhotoCount": 3
  }
}
```

### Fail Response

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_REQUEST` | `Idempotency-Key` 누락·UUID 형식 오류 또는 요청 본문 형식 오류 |
| 400 | `INVALID_SHARED_ALBUM_IDS` | 요청 본문이 `null`이거나, `sharedAlbumIds`가 비었거나 `null` 원소를 포함하거나, 중복이 있거나, 100개를 초과함 |
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `SHARED_ALBUM_NOT_FOUND` | 대상 중 하나라도 공유집(앨범)이 없거나 활성 멤버십이 없음 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 `Idempotency-Key`를 다른 요청에 재사용함 |
| 409 | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 요청이 아직 처리 중임 |
