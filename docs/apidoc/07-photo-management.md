# 사진 업로드·관리 API 명세

## 1. Notion DB 등록 정보

| ID | index | 이름 | HTTP Method | API Path | 상태 | 구현 여부 | 연동 o/x |
|---|---|---|---|---|---|---|---|
| PHOTO-01 | 사진 | 공유 폴더 사진 목록 조회 | GET | `/api/v1/shared-folders/{sharedFolderId}/photos` | 시작 전 | false | false |
| PHOTO-02 | 사진 | 공유 폴더 사진 업로드 | POST | `/api/v1/shared-folders/{sharedFolderId}/photos` | 시작 전 | false | false |
| PHOTO-03 | 사진 | 사진 촬영일시 수정 | PATCH | `/api/v1/photos/{photoId}` | 시작 전 | false | false |
| PHOTO-04 | 사진 | 사진 삭제 | DELETE | `/api/v1/photos/{photoId}` | 시작 전 | false | false |
| PHOTO-05 | 사진 | 사진 일괄 삭제 | POST | `/api/v1/shared-folders/{sharedFolderId}/photos/bulk-delete` | 시작 전 | false | false |

모든 API는 대상 공유 폴더의 활성 멤버십을 요구한다. 사진 수정·삭제는 업로더만 가능하다. 이미지 원본은 OCI Object Storage에 저장하고 API는 `objectKey` 대신 접근 가능한 `imageUrl`을 반환한다. 공통 응답과 오류는 [01-common-spec.md](01-common-spec.md)를 따른다.

## 2. PHOTO-01 공유 폴더 사진 목록 조회

`displayAt = takenAt ?? createdAt` 내림차순으로 조회하며 동일 시각은 사진 ID로 순서를 고정한다. 날짜 그룹은 클라이언트의 표시 시간대에서 계산한다.

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
| `sharedAlbumId` | UUID | X | null | 지정하면 해당 앨범의 활성 포함 관계에 속한 사진만 조회 |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "PHOTO_LIST_FOUND",
  "message": "사진 목록을 조회했습니다.",
  "data": {
    "items": [
      {
        "id": "385ff765-b20c-49a2-8e62-e1457784aa15",
        "imageUrl": "https://object.example.com/signed/photo.jpg",
        "imageUrlExpiresAt": "2026-07-03T11:15:30Z",
        "originalFileName": "IMG_0001.HEIC",
        "contentType": "image/heic",
        "fileSize": 2849182,
        "takenAt": "2026-06-30T04:20:00Z",
        "displayAt": "2026-06-30T04:20:00Z",
        "uploadedBy": {
          "userId": "018f0c3e-2c77-7d72-a37e-2f5666f25d32",
          "displayName": "집집이"
        },
        "isUploader": true,
        "likeCount": 3,
        "commentCount": 2,
        "isLikedByMe": true,
        "createdAt": "2026-07-03T10:15:30Z"
      }
    ],
    "nextCursor": null,
    "hasNext": false
  }
}
```

`imageUrl`은 만료 가능한 서명 URL이므로 영구 저장하지 않는다. 만료되면 이 API 또는 사진 상세 API를 다시 호출한다.

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_CURSOR` | cursor가 유효하지 않음 |
| 404 | `SHARED_FOLDER_NOT_FOUND` | 공유 폴더 또는 활성 멤버십이 없음 |
| 404 | `SHARED_ALBUM_NOT_FOUND` | 필터 앨범이 없거나 다른 공유 폴더에 속함 |

## 3. PHOTO-02 공유 폴더 사진 업로드

한 요청에서 최대 20개 이미지를 공유 폴더에 업로드한다. 서버는 각 이미지의 EXIF에서 촬영일시를 추출해 `takenAt`에 저장하고, 값이 없으면 `null`로 보존한다. EXIF를 제거하지 않은 원본 파일을 Object Storage에 저장한다. 각 파일은 같은 공유 폴더의 0개 이상 앨범에 동시에 포함할 수 있다. 전체 요청은 원자적으로 처리하며 한 파일이라도 검증·저장에 실패하면 생성한 객체와 DB 행을 정리하고 전체를 실패 처리한다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Authorization` | String | O | Bearer Access Token | `Bearer eyJhbGciOi...` |
| `Idempotency-Key` | UUID String | O | 사진 업로드 재시도 식별자 | `54cf8d7e-a23e-4e76-90f7-603f122b1507` |
| `Content-Type` | String | O | multipart boundary 포함 | `multipart/form-data` |

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedFolderId` | UUID | O | 사진을 업로드할 공유 폴더 식별자 |

#### Body

`multipart/form-data`

| Part | 타입 | 필수 | 제약 | 설명 |
|---|---|---:|---|---|
| `files` | Binary[] | O | 1~20개, 각 20 MiB 이하, `image/*` | 업로드할 이미지 파일 반복 part |
| `metadata` | JSON | O | 파일 수와 동일한 배열 | `fileIndex`는 `files` 반복 순서와 일치하고 `sharedAlbumIds`는 중복 없는 배열 |

`metadata` 예시:

```json
[
  {
    "fileIndex": 0,
    "sharedAlbumIds": ["59ce0d18-a53e-4197-9c3c-e82331adc097"]
  },
  {
    "fileIndex": 1,
    "sharedAlbumIds": []
  }
]
```

### Success Response ✓

#### HTTP Status Code: `201 Created`

```json
{
  "status": 201,
  "code": "PHOTOS_CREATED",
  "message": "사진을 업로드했습니다.",
  "data": {
    "items": [
      {
        "id": "385ff765-b20c-49a2-8e62-e1457784aa15",
        "sharedFolderId": "b8a5f612-25d7-4ec3-9d1d-59684de40664",
        "sharedAlbumIds": ["59ce0d18-a53e-4197-9c3c-e82331adc097"],
        "imageUrl": "https://object.example.com/signed/photo.jpg",
        "imageUrlExpiresAt": "2026-07-03T11:15:30Z",
        "originalFileName": "IMG_0001.HEIC",
        "contentType": "image/heic",
        "fileSize": 2849182,
        "takenAt": "2026-06-30T04:20:00Z",
        "createdAt": "2026-07-03T10:15:30Z"
      }
    ]
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_UPLOAD_METADATA` | 파일 수, index 또는 앨범 식별자 배열이 유효하지 않음 |
| 400 | `TOO_MANY_FILES` | 한 요청에 20개를 초과함 |
| 404 | `SHARED_FOLDER_NOT_FOUND` | 공유 폴더 또는 활성 멤버십이 없음 |
| 404 | `SHARED_ALBUM_NOT_FOUND` | 지정한 앨범이 없거나 다른 공유 폴더에 속함 |
| 413 | `FILE_TOO_LARGE` | 한 파일이 20 MiB를 초과함 |
| 415 | `UNSUPPORTED_IMAGE_TYPE` | MIME type이 `image/*`가 아님 |
| 502 | `OBJECT_STORAGE_UPLOAD_FAILED` | Object Storage 업로드 실패 |

## 4. PHOTO-03 사진 촬영일시 수정

`takenAt`을 `null`로 보내면 촬영일시를 제거하고 이후 `createdAt`을 표시·정렬 기준으로 사용한다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `photoId` | UUID | O | 사진 식별자 |

#### Body

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `takenAt` | String, null | O | UTC ISO-8601 촬영일시 또는 제거를 위한 null | `2026-06-30T04:20:00Z` |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "PHOTO_UPDATED",
  "message": "사진 촬영일시를 수정했습니다.",
  "data": {
    "id": "385ff765-b20c-49a2-8e62-e1457784aa15",
    "takenAt": "2026-06-30T04:20:00Z",
    "displayAt": "2026-06-30T04:20:00Z",
    "updatedAt": "2026-07-03T12:00:00Z"
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_TAKEN_AT` | UTC ISO-8601 형식이 아님 |
| 403 | `NOT_PHOTO_UPLOADER` | 활성 멤버지만 업로더가 아님 |
| 404 | `PHOTO_NOT_FOUND` | 사진 또는 상위 활성 리소스·멤버십이 없음 |

## 5. PHOTO-04 사진 삭제

사진을 soft delete해 즉시 접근을 차단하고 30일 뒤 Object Storage 객체 삭제가 성공한 경우에만 DB 행을 물리 삭제한다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `photoId` | UUID | O | 삭제할 사진 식별자 |

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "PHOTO_DELETED",
  "message": "사진을 삭제했습니다.",
  "data": null
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 403 | `NOT_PHOTO_UPLOADER` | 활성 멤버지만 업로더가 아님 |
| 404 | `PHOTO_NOT_FOUND` | 사진 또는 상위 활성 리소스·멤버십이 없음 |

## 6. PHOTO-05 사진 일괄 삭제

같은 요청 사용자가 업로드하고 같은 공유 폴더에 속한 사진만 한 번에 최대 100개 soft delete한다. 모든 사진을 검증한 뒤 하나의 트랜잭션으로 처리하며 일부 성공은 허용하지 않는다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Idempotency-Key` | UUID String | O | 일괄 삭제 재시도 식별자 | `54cf8d7e-a23e-4e76-90f7-603f122b1507` |

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedFolderId` | UUID | O | 사진을 일괄 삭제할 공유 폴더 식별자 |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---:|---|---|
| `photoIds` | UUID[] | O | 중복 없이 1~100개 | 삭제할 사진 식별자 목록 |

```json
{
  "photoIds": [
    "385ff765-b20c-49a2-8e62-e1457784aa15",
    "4c9410ec-1dfa-4dbb-b8e6-2a4e947256c9"
  ]
}
```

### Success Response ✓

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "PHOTOS_DELETED",
  "message": "사진을 일괄 삭제했습니다.",
  "data": {
    "deletedCount": 2
  }
}
```

### Fail Response Ⓧ

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_PHOTO_IDS` | 배열이 비었거나 중복·형식 오류·100개 초과 |
| 403 | `NOT_PHOTO_UPLOADER` | 대상 중 요청 사용자가 업로드하지 않은 사진이 있음 |
| 404 | `SHARED_FOLDER_NOT_FOUND` | 공유 폴더 또는 활성 멤버십이 없음 |
| 404 | `PHOTO_NOT_FOUND` | 대상 중 접근 가능한 활성 사진이 아닌 항목이 있음 |
