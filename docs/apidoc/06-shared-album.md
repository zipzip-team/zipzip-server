# 공유집(앨범) API 명세

## 1. Notion DB 등록 정보

공유집(앨범)은 공유 그룹 하위 리소스이며 물리 모델은 `shared_album`이다.
사진은 반드시 하나의 공유집(앨범)에 직접 속하므로 별도 앨범 사진 포함 관계 API는 제공하지 않는다.

| ID | index | 이름 | HTTP Method | API Path | 상태 | 구현 여부 | 연동 o/x |
|---|---|---|---|---|---|---|---|
| ALBUM-01 | 앨범 | 공유집(앨범) 목록 조회 | GET | `/api/v1/shared-groups/{sharedGroupId}/shared-albums` | 시작 전 | false | false |
| ALBUM-02 | 앨범 | 공유집(앨범) 생성 | POST | `/api/v1/shared-groups/{sharedGroupId}/shared-albums` | 시작 전 | false | false |
| ALBUM-03 | 앨범 | 공유집(앨범) 상세 조회 | GET | `/api/v1/shared-albums/{sharedAlbumId}` | 시작 전 | false | false |
| ALBUM-04 | 앨범 | 공유집(앨범) 이름 수정 | PATCH | `/api/v1/shared-albums/{sharedAlbumId}` | 시작 전 | false | false |
| ALBUM-05 | 앨범 | 공유집(앨범) 삭제 | DELETE | `/api/v1/shared-albums/{sharedAlbumId}` | 시작 전 | false | false |

모든 API는 대상 공유 그룹의 활성 멤버십을 요구한다.
방장과 멤버 모두 공유집(앨범)을 생성·수정할 수 있으며, 삭제는 생성자가 할 수 있다. 생성자가 탈퇴한 사용자인 경우 상위 공유 그룹 방장도 삭제할 수 있다.
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
| `cursor` | String | X | null | 이전 응답의 불투명 cursor |
| `size` | Integer | X | 20 | 1~100 |

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
| 404 | `SHARED_GROUP_NOT_FOUND` | 공유 그룹 또는 활성 멤버십이 없음 |

## 3. ALBUM-02 공유집(앨범) 생성

사진이 없는 빈 공유집(앨범)도 생성할 수 있다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Idempotency-Key` | UUID String | O | 생성 재시도 식별자 | `54cf8d7e-a23e-4e76-90f7-603f122b1507` |

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedGroupId` | UUID | O | 공유 그룹 식별자 |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 | 예시 |
|---|---|---:|---|---|---|
| `name` | String | O | trim 후 1~100자 | 공유집(앨범) 이름 | `제주도` |

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
| 400 | `INVALID_SHARED_ALBUM_NAME` | 이름이 공백이거나 100자를 초과함 |
| 404 | `SHARED_GROUP_NOT_FOUND` | 공유 그룹 또는 활성 멤버십이 없음 |

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
| `name` | String | O | trim 후 1~100자 | 새 공유집(앨범) 이름 |

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
| 400 | `INVALID_SHARED_ALBUM_NAME` | 이름 제약 위반 |
| 404 | `SHARED_ALBUM_NOT_FOUND` | 공유집(앨범) 또는 활성 멤버십이 없음 |

## 6. ALBUM-05 공유집(앨범) 삭제

공유집(앨범)을 soft delete해 해당 공유집(앨범)과 하위 사진 접근을 즉시 차단한다.
30일 뒤 하위 사진의 Object Storage 객체 삭제가 끝나면 공유집(앨범) 행을 물리 삭제한다.

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
| 403 | `NOT_SHARED_ALBUM_CREATOR` | 활성 멤버지만 생성자가 아니며, 탈퇴한 생성자의 공유 그룹 방장도 아님 |
| 404 | `SHARED_ALBUM_NOT_FOUND` | 공유집(앨범) 또는 활성 멤버십이 없음 |
