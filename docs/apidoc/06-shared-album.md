# 앨범 API 명세

## 1. Notion DB 등록 정보

앨범은 공유 폴더 하위 리소스지만 독립적인 CRUD와 권한 경계를 가지므로 `앨범` index로 분류한다.

| ID | index | 이름 | HTTP Method | API Path | 상태 | 구현 여부 | 연동 o/x |
|---|---|---|---|---|---|---|---|
| ALBUM-01 | 앨범 | 앨범 목록 조회 | GET | `/api/v1/shared-folders/{sharedFolderId}/shared-albums` | 시작 전 | false | false |
| ALBUM-02 | 앨범 | 앨범 생성 | POST | `/api/v1/shared-folders/{sharedFolderId}/shared-albums` | 시작 전 | false | false |
| ALBUM-03 | 앨범 | 앨범 상세 조회 | GET | `/api/v1/shared-albums/{sharedAlbumId}` | 시작 전 | false | false |
| ALBUM-04 | 앨범 | 앨범 이름 수정 | PATCH | `/api/v1/shared-albums/{sharedAlbumId}` | 시작 전 | false | false |
| ALBUM-05 | 앨범 | 앨범 삭제 | DELETE | `/api/v1/shared-albums/{sharedAlbumId}` | 시작 전 | false | false |
| ALBUM-06 | 앨범 | 앨범에 사진 추가 | PUT | `/api/v1/shared-albums/{sharedAlbumId}/photos/{photoId}` | 시작 전 | false | false |
| ALBUM-07 | 앨범 | 앨범에서 사진 제거 | DELETE | `/api/v1/shared-albums/{sharedAlbumId}/photos/{photoId}` | 시작 전 | false | false |

모든 API는 대상 공유 폴더의 활성 멤버십을 요구한다. 방장과 멤버 모두 앨범을 생성·수정하고 사진 포함 관계를 추가·제거할 수 있으며, 앨범 삭제는 생성자만 할 수 있다. 공통 응답과 오류는 [01-common-spec.md](01-common-spec.md)를 따른다.

## 2. ALBUM-01 앨범 목록 조회

생성일시 내림차순으로 조회하며 동일 시각은 앨범 ID로 순서를 고정한다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedFolderId` | UUID | O | 공유 폴더 식별자 |

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
  "code": "SHARED_ALBUM_LIST_FOUND",
  "message": "앨범 목록을 조회했습니다.",
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

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_CURSOR` | cursor가 유효하지 않음 |
| 404 | `SHARED_FOLDER_NOT_FOUND` | 공유 폴더 또는 활성 멤버십이 없음 |

## 3. ALBUM-02 앨범 생성

사진이 없는 빈 앨범도 생성할 수 있다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Idempotency-Key` | UUID String | O | 앨범 생성 재시도 식별자 | `54cf8d7e-a23e-4e76-90f7-603f122b1507` |

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedFolderId` | UUID | O | 공유 폴더 식별자 |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 | 예시 |
|---|---|---:|---|---|---|
| `name` | String | O | trim 후 1~100자 | 앨범 이름 | `제주도` |

### Success Response ✓

#### HTTP Status Code: `201 Created`

```json
{
  "status": 201,
  "code": "SHARED_ALBUM_CREATED",
  "message": "앨범을 생성했습니다.",
  "data": {
    "id": "59ce0d18-a53e-4197-9c3c-e82331adc097",
    "sharedFolderId": "b8a5f612-25d7-4ec3-9d1d-59684de40664",
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

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_SHARED_ALBUM_NAME` | 이름이 공백이거나 100자를 초과함 |
| 404 | `SHARED_FOLDER_NOT_FOUND` | 공유 폴더 또는 활성 멤버십이 없음 |

## 4. ALBUM-03 앨범 상세 조회

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedAlbumId` | UUID | O | 앨범 식별자 |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "SHARED_ALBUM_FOUND",
  "message": "앨범을 조회했습니다.",
  "data": {
    "id": "59ce0d18-a53e-4197-9c3c-e82331adc097",
    "sharedFolderId": "b8a5f612-25d7-4ec3-9d1d-59684de40664",
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

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 404 | `SHARED_ALBUM_NOT_FOUND` | 앨범이 없거나 상위 공유 폴더의 활성 멤버십이 없음 |

## 5. ALBUM-04 앨범 이름 수정

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedAlbumId` | UUID | O | 앨범 식별자 |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---:|---|---|
| `name` | String | O | trim 후 1~100자 | 새 앨범 이름 |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "SHARED_ALBUM_UPDATED",
  "message": "앨범 이름을 수정했습니다.",
  "data": {
    "id": "59ce0d18-a53e-4197-9c3c-e82331adc097",
    "name": "제주 여름",
    "updatedAt": "2026-07-03T12:00:00Z"
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_SHARED_ALBUM_NAME` | 이름 제약 위반 |
| 404 | `SHARED_ALBUM_NOT_FOUND` | 앨범 또는 활성 멤버십이 없음 |

## 6. ALBUM-05 앨범 삭제

앨범을 soft delete해 해당 앨범과 포함 관계를 조회에서 즉시 제외한다. 사진 원본은 공유 폴더에 남아 다른 앨범과 공유 폴더 사진 목록에서 계속 접근할 수 있다. 30일 뒤 앨범 행을 물리 삭제할 때 포함 관계만 FK cascade로 정리한다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedAlbumId` | UUID | O | 앨범 식별자 |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "SHARED_ALBUM_DELETED",
  "message": "앨범을 삭제했습니다.",
  "data": null
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 403 | `NOT_SHARED_ALBUM_CREATOR` | 활성 멤버지만 생성자가 아님 |
| 404 | `SHARED_ALBUM_NOT_FOUND` | 앨범 또는 활성 멤버십이 없음 |

## 7. ALBUM-06 앨범에 사진 추가

같은 공유 폴더의 활성 사진을 앨범에 포함한다. 이미 포함된 사진을 다시 추가해도 성공하는 멱등 API이며 새 사진 원본을 만들지 않는다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedAlbumId` | UUID | O | 사진을 추가할 앨범 식별자 |
| `photoId` | UUID | O | 앨범에 포함할 사진 식별자 |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "SHARED_ALBUM_PHOTO_ADDED",
  "message": "앨범에 사진을 추가했습니다.",
  "data": {
    "sharedAlbumId": "59ce0d18-a53e-4197-9c3c-e82331adc097",
    "photoId": "385ff765-b20c-49a2-8e62-e1457784aa15",
    "addedAt": "2026-07-03T12:00:00Z"
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 404 | `SHARED_ALBUM_NOT_FOUND` | 앨범 또는 활성 멤버십이 없음 |
| 404 | `PHOTO_NOT_FOUND` | 사진이 없거나 앨범과 다른 공유 폴더에 속함 |

## 8. ALBUM-07 앨범에서 사진 제거

`shared_album_photo` 포함 관계만 즉시 물리 삭제한다. 사진 원본과 다른 앨범의 포함 관계는 유지하며, 이미 제거된 관계도 성공하는 멱등 API다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedAlbumId` | UUID | O | 사진을 제거할 앨범 식별자 |
| `photoId` | UUID | O | 앨범에서만 제거할 사진 식별자 |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "SHARED_ALBUM_PHOTO_REMOVED",
  "message": "앨범에서 사진을 제거했습니다.",
  "data": null
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 404 | `SHARED_ALBUM_NOT_FOUND` | 앨범 또는 활성 멤버십이 없음 |
| 404 | `PHOTO_NOT_FOUND` | 사진이 없거나 앨범과 다른 공유 폴더에 속함 |
