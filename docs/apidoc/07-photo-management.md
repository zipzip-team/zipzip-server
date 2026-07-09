# 사진 업로드·관리 API 명세

## 1. Notion DB 등록 정보

사진은 공유 그룹에 직접 속하지 않고, `shared_album_photo`를 통해서만 하나 이상의 공유집(앨범)에 속한다(공유 위계는 공유 그룹 > 공유집(앨범) > 사진). 사진은 항상 1개 이상의 공유집(앨범)에 속해야 한다.
사진 목록 조회와 업로드 API는 공유집(앨범) 식별자인 `sharedAlbumId`를 기준으로 한다.

| ID | index | 이름 | HTTP Method | API Path | 상태 | 구현 여부 | 연동 o/x |
|---|---|---|---|---|---|---|---|
| PHOTO-01 | 사진 | 공유집(앨범) 사진 목록 조회 | GET | `/api/v1/shared-albums/{sharedAlbumId}/photos` | 시작 전 | false | false |
| PHOTO-02 | 사진 | 사진 업로드 URL 발급 | POST | `/api/v1/shared-albums/{sharedAlbumId}/photos/upload-urls` | 시작 전 | false | false |
| PHOTO-03 | 사진 | 사진 업로드 완료 등록 | POST | `/api/v1/shared-albums/{sharedAlbumId}/photos/complete` | 시작 전 | false | false |
| PHOTO-04 | 사진 | 사진 메타데이터 수정 | PATCH | `/api/v1/photos/{photoId}` | 시작 전 | false | false |
| PHOTO-05 | 사진 | 사진 삭제 | DELETE | `/api/v1/photos/{photoId}` | 시작 전 | false | false |
| PHOTO-06 | 사진 | 공유집(앨범) 사진 일괄 삭제 | POST | `/api/v1/shared-albums/{sharedAlbumId}/photos/bulk-delete` | 시작 전 | false | false |
| PHOTO-07 | 사진 | 공유집(앨범)에 기존 사진 추가 | POST | `/api/v1/shared-albums/{sharedAlbumId}/photos/attach` | 시작 전 | false | false |
| PHOTO-08 | 사진 | 공유집(앨범)에서 사진 제거 | POST | `/api/v1/shared-albums/{sharedAlbumId}/photos/detach` | 시작 전 | false | false |

모든 API는 대상 공유집(앨범) 또는 사진이 속한 공유 그룹의 활성 멤버십을 요구한다.
사진 메타데이터 수정은 업로더만 가능하다. 사진 삭제는 원칙적으로 업로더만 가능하며, 업로더가 탈퇴한 사용자인 경우 상위 공유 그룹 방장도 삭제할 수 있다.
공유집(앨범)에 사진을 추가·제거하는 것은 활성 방장·멤버 누구나 할 수 있다. 제거 대상 사진이 다른 공유집(앨범)에도 속해 있으면 해당 앨범-사진 매핑만 없애고 사진 원본은 유지되지만, 제거하려는 공유집(앨범)이 그 사진의 마지막 소속이면 사진 원본도 함께 soft delete된다(자세한 내용은 [PHOTO-08](#9-photo-08-공유집앨범에서-사진-제거) 참고).

원본 이미지는 서버를 거치지 않고 iOS와 OCI Object Storage 사이에서 직접 오간다.
서버는 `photo.original_object_key`, `photo.thumbnail_object_key`만 저장하고, API 조회 시점마다 presigned URL을 새로 발급한다.
썸네일은 업로드 완료 등록 직후 서버가 원본을 다운로드해 비동기로 생성하며, 진행 상태는 `thumbnailStatus`로 노출한다.
촬영 기기명·촬영일시·위치·이미지 크기 같은 메타데이터는 iOS가 EXIF에서 추출해 완료 등록 요청에 실어 보내고, 서버는 이를 그대로 저장한다.
공통 응답과 오류는 [01-common-spec.md](01-common-spec.md)를 따른다.

## 2. PHOTO-01 공유집(앨범) 사진 목록 조회

`shared_album_photo`로 대상 공유집(앨범)에 속한 사진을 조회한다.
`displayAt = takenAt ?? createdAt` 내림차순으로 조회하며 동일 시각은 사진 ID로 순서를 고정한다.
날짜 그룹은 클라이언트의 표시 시간대에서 계산한다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedAlbumId` | UUID | O | 공유집(앨범) 식별자 |

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
  "code": "PHOTO_LIST_FOUND",
  "message": "사진 목록을 조회했습니다.",
  "data": {
    "items": [
      {
        "id": "385ff765-b20c-49a2-8e62-e1457784aa15",
        "sharedGroupId": "b8a5f612-25d7-4ec3-9d1d-59684de40664",
        "sharedAlbumId": "59ce0d18-a53e-4197-9c3c-e82331adc097",
        "originalUrl": "https://objectstorage.example.com/signed/385ff765-original.jpg",
        "originalUrlExpiresAt": "2026-07-03T10:25:30Z",
        "thumbnailUrl": "https://objectstorage.example.com/signed/385ff765-thumb.jpg",
        "thumbnailUrlExpiresAt": "2026-07-03T10:25:30Z",
        "thumbnailStatus": "READY",
        "deviceModel": "iPhone 15",
        "takenAt": "2026-06-30T04:20:00Z",
        "displayAt": "2026-06-30T04:20:00Z",
        "latitude": 33.450701,
        "longitude": 126.570667,
        "locationName": "제주특별자치도 제주시",
        "isInferred": false,
        "width": 4032,
        "height": 3024,
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

`thumbnailStatus`가 `PENDING`이면 `thumbnailUrl`, `thumbnailUrlExpiresAt`은 `null`이다. `FAILED`면 썸네일 없이 `originalUrl`만 표시한다.
`deviceModel`, `takenAt`, `latitude`, `longitude`, `locationName`, `width`, `height`는 iOS가 전달하지 않았으면 `null`이다.
`originalUrl`, `thumbnailUrl`은 매 요청마다 새로 발급하는 presigned URL이므로 영구 저장하지 않는다. 만료되면 이 API 또는 사진 상세 API를 다시 호출한다.

### Fail Response

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_CURSOR` | cursor가 유효하지 않음 |
| 404 | `SHARED_ALBUM_NOT_FOUND` | 공유집(앨범) 또는 활성 멤버십이 없음 |

## 3. PHOTO-02 사진 업로드 URL 발급

한 요청에서 최대 20개까지 원본 업로드용 presigned PUT URL을 발급한다.
이 단계에서는 아직 `photo` 행을 만들지 않는다. 발급한 URL로 실제 업로드가 끝난 뒤 [PHOTO-03 사진 업로드 완료 등록](#4-photo-03-사진-업로드-완료-등록)을 호출해야 사진이 생성된다.
발급한 각 URL은 정해진 시간 안에 정해진 `objectKey`로만 `PUT` 할 수 있도록 서명 조건에 파일 크기·MIME type 제약을 포함한다.
서버는 발급한 각 `objectKey`를 요청 사용자·대상 공유집(앨범)·만료 시각과 함께 `photo_upload_reservation`에 기록한다. 이 예약 행은 PHOTO-03이 `objectKey`의 발급 대상(사용자·공유집(앨범))과 재사용 여부를 검증하는 근거이며, 완료 등록에 성공하면 삭제한다. 만료된 미완료 예약과 그에 대응하는 Object Storage 객체는 정기 스윕이 정리한다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Authorization` | String | O | Bearer Access Token | `Bearer eyJhbGciOi...` |

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedAlbumId` | UUID | O | 사진을 업로드할 공유집(앨범) 식별자 |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---:|---|---|
| `files` | Object[] | O | 1~20개 | 발급할 업로드 URL 목록 |
| `files[].contentType` | String | O | `image/*` | 업로드할 이미지의 MIME type |
| `files[].sizeBytes` | Long | O | 1 이상, 20 MiB 이하 | 업로드할 파일 크기. presigned URL의 서명 조건에도 포함되어 다른 크기로는 업로드할 수 없다 |

```json
{
  "files": [
    { "contentType": "image/jpeg", "sizeBytes": 2849182 },
    { "contentType": "image/jpeg", "sizeBytes": 3120044 }
  ]
}
```

### Success Response

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "PHOTO_UPLOAD_URLS_ISSUED",
  "message": "사진 업로드 URL을 발급했습니다.",
  "data": {
    "uploads": [
      {
        "objectKey": "photos/2026/07/08/9c3d1e2a-4b7f-4e2a-8c8e-2f5666f25d32.jpg",
        "uploadUrl": "https://objectstorage.example.com/signed-put/9c3d1e2a.jpg",
        "uploadUrlExpiresAt": "2026-07-03T10:25:30Z"
      }
    ]
  }
}
```

`objectKey`는 [PHOTO-03 사진 업로드 완료 등록](#4-photo-03-사진-업로드-완료-등록) 요청에 그대로 사용한다.
`uploadUrl`은 `files` 배열과 같은 순서로 반환한다.

### Fail Response

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_UPLOAD_METADATA` | `files`가 비었거나 형식이 잘못됨 |
| 400 | `TOO_MANY_FILES` | 한 요청에 20개를 초과함 |
| 404 | `SHARED_ALBUM_NOT_FOUND` | 공유집(앨범) 또는 활성 멤버십이 없음 |
| 413 | `FILE_TOO_LARGE` | 대상 중 `sizeBytes`가 20 MiB를 초과하는 항목이 있음 |
| 415 | `UNSUPPORTED_IMAGE_TYPE` | 대상 중 `contentType`이 `image/*`가 아닌 항목이 있음 |

## 4. PHOTO-03 사진 업로드 완료 등록

[PHOTO-02](#3-photo-02-사진-업로드-url-발급)에서 발급받은 `objectKey`로 원본을 직접 업로드한 뒤, 업로드가 끝난 파일들을 한 번에 등록한다.
서버는 각 `objectKey`에 대해 (1) 요청 사용자·요청 경로 공유집(앨범)과 일치하고 만료되지 않은 `photo_upload_reservation` 행이 있는지, (2) Object Storage에 실제 업로드가 끝났는지를 확인한다. 두 조건을 모두 만족하면 한 트랜잭션으로 `photo`와 `shared_album_photo` 매핑을 생성하고 해당 예약 행을 삭제한다. 이 매핑이 사진의 유일한 소속이 되며, 사진은 공유 그룹에 직접 속하지 않는다(응답의 `sharedGroupId`는 이 매핑을 통해 조회 시점에 계산한 값이다).
예약 행이 없으면(발급된 적이 없거나, 다른 사용자·다른 공유집(앨범)에 발급됐거나, 이미 등록에 사용됐거나, 만료됨) `objectKey`를 신뢰하지 않는다.
생성된 사진의 `thumbnailStatus`는 `PENDING`으로 시작하며, 서버가 즉시 비동기 썸네일 생성 작업에 제출한다.
전체 요청은 원자적으로 처리하며 한 파일이라도 검증에 실패하면 전체를 실패 처리한다(이미 Object Storage에 올라간 원본 객체는 남아있을 수 있으며 정기 스윕이 정리한다).
촬영 기기명·촬영일시·위치·이미지 크기는 iOS가 EXIF에서 추출해 값을 넘긴 경우에만 저장하고, 넘기지 않으면 `null`로 보존한다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Idempotency-Key` | UUID String | O | 완료 등록 재시도 식별자 | `54cf8d7e-a23e-4e76-90f7-603f122b1507` |

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedAlbumId` | UUID | O | 사진을 등록할 공유집(앨범) 식별자 |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---:|---|---|
| `files` | Object[] | O | 중복 없이 1~20개 | 등록할 파일 목록 |
| `files[].objectKey` | String | O | PHOTO-02에서 발급받은 값 | 업로드를 완료한 객체 키 |
| `files[].deviceModel` | String, null | X | trim 후 100자 이하 | EXIF에서 추출한 촬영 기기명. 없으면 생략 |
| `files[].takenAt` | String, null | X | UTC ISO-8601 | EXIF 촬영일시. 없으면 생략 |
| `files[].latitude` | Number, null | X | `longitude`, `locationName`과 함께 사용 | 촬영 위치 위도 |
| `files[].longitude` | Number, null | X | `latitude`, `locationName`과 함께 사용 | 촬영 위치 경도 |
| `files[].locationName` | String, null | X | `latitude`, `longitude`와 함께 사용 | 촬영 위치명 |
| `files[].isInferred` | Boolean | X | 기본값 `false` | 위치정보가 iOS 메타데이터 제안 UI로 추론된 값인지 여부 |
| `files[].width` | Integer | X |  | 이미지 가로 픽셀 |
| `files[].height` | Integer | X |  | 이미지 세로 픽셀 |

```json
{
  "files": [
    {
      "objectKey": "photos/2026/07/08/9c3d1e2a-4b7f-4e2a-8c8e-2f5666f25d32.jpg",
      "deviceModel": "iPhone 15",
      "takenAt": "2026-06-30T04:20:00Z",
      "latitude": 33.450701,
      "longitude": 126.570667,
      "locationName": "제주특별자치도 제주시",
      "isInferred": false,
      "width": 4032,
      "height": 3024
    }
  ]
}
```

### Success Response

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
        "sharedGroupId": "b8a5f612-25d7-4ec3-9d1d-59684de40664",
        "sharedAlbumId": "59ce0d18-a53e-4197-9c3c-e82331adc097",
        "originalUrl": "https://objectstorage.example.com/signed/385ff765-original.jpg",
        "originalUrlExpiresAt": "2026-07-03T10:25:30Z",
        "thumbnailUrl": null,
        "thumbnailUrlExpiresAt": null,
        "thumbnailStatus": "PENDING",
        "deviceModel": "iPhone 15",
        "takenAt": "2026-06-30T04:20:00Z",
        "latitude": 33.450701,
        "longitude": 126.570667,
        "locationName": "제주특별자치도 제주시",
        "isInferred": false,
        "width": 4032,
        "height": 3024,
        "createdAt": "2026-07-03T10:15:30Z"
      }
    ]
  }
}
```

### Fail Response

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_UPLOAD_METADATA` | `files`가 비었거나 20개를 초과하거나 필드 형식이 잘못됨(`deviceModel`이 100자를 초과하는 경우 포함) |
| 404 | `SHARED_ALBUM_NOT_FOUND` | 공유집(앨범) 또는 활성 멤버십이 없음 |
| 404 | `UPLOAD_OBJECT_NOT_FOUND` | `objectKey`에 대응하는 `photo_upload_reservation`이 없음(발급받은 적 없음, 다른 사용자·다른 공유집(앨범)에 발급됨, 이미 등록에 사용함, 만료됨을 모두 포함) |
| 409 | `UPLOAD_NOT_COMPLETED` | 예약은 유효하지만 `objectKey`로 원본이 아직 Object Storage에 업로드되지 않음 |

## 5. PHOTO-04 사진 메타데이터 수정

`takenAt`을 `null`로 보내면 촬영일시를 제거하고 이후 `createdAt`을 표시·정렬 기준으로 사용한다.
위치 필드(`latitude`, `longitude`, `locationName`)는 세 값을 함께 보내 갱신하거나 세 값 모두 `null`로 보내 제거한다.
`isInferred`는 위치 필드와 함께 보낼 때만 반영하며, 생략하면 `false`로 저장한다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `photoId` | UUID | O | 사진 식별자 |

#### Body

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `takenAt` | String, null | X | UTC ISO-8601 촬영일시 또는 제거를 위한 null | `2026-06-30T04:20:00Z` |
| `latitude` | Number, null | X | 위도. `longitude`, `locationName`과 함께 사용 | `33.450701` |
| `longitude` | Number, null | X | 경도. `latitude`, `locationName`과 함께 사용 | `126.570667` |
| `locationName` | String, null | X | 위치명. `latitude`, `longitude`와 함께 사용 | `제주특별자치도 제주시` |
| `isInferred` | Boolean | X | 위치정보 추론 여부. 위치 필드와 함께 전달 | `false` |

### Success Response

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "PHOTO_UPDATED",
  "message": "사진 메타데이터를 수정했습니다.",
  "data": {
    "id": "385ff765-b20c-49a2-8e62-e1457784aa15",
    "takenAt": "2026-06-30T04:20:00Z",
    "displayAt": "2026-06-30T04:20:00Z",
    "latitude": 33.450701,
    "longitude": 126.570667,
    "locationName": "제주특별자치도 제주시",
    "isInferred": false,
    "updatedAt": "2026-07-03T12:00:00Z"
  }
}
```

### Fail Response

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_TAKEN_AT` | UTC ISO-8601 형식이 아님 |
| 400 | `INVALID_PHOTO_LOCATION` | 위치 필드 세 값 중 일부만 전달함 |
| 403 | `NOT_PHOTO_UPLOADER` | 활성 멤버지만 업로더가 아님 |
| 404 | `PHOTO_NOT_FOUND` | 사진 또는 상위 활성 리소스·멤버십이 없음 |

## 6. PHOTO-05 사진 삭제

사진을 soft delete해 즉시 접근을 차단하고 30일 뒤 Object Storage 원본·썸네일 객체 삭제가 성공한 경우에만 DB 행을 물리 삭제한다.
사진이 여러 공유집(앨범)에 속해 있어도 삭제는 사진 원본 자체를 대상으로 하며, 소속된 모든 공유집(앨범)에서 함께 접근이 차단된다.
휴지통이나 복구 기능은 제공하지 않으므로 클라이언트는 삭제 전 확인 다이얼로그로 되돌릴 수 없음을 안내해야 한다.
특정 공유집(앨범)에서만 사진을 빼고 싶다면 [PHOTO-08 공유집(앨범)에서 사진 제거](#9-photo-08-공유집앨범에서-사진-제거)를 사용한다.

### Request

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `photoId` | UUID | O | 삭제할 사진 식별자 |

### Success Response

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "PHOTO_DELETED",
  "message": "사진을 삭제했습니다.",
  "data": null
}
```

### Fail Response

| HTTP Status | code | 조건 |
|---:|---|---|
| 403 | `NOT_PHOTO_UPLOADER` | 활성 멤버지만 업로더가 아니며, 탈퇴한 업로더의 공유 그룹 방장도 아님 |
| 404 | `PHOTO_NOT_FOUND` | 사진 또는 상위 활성 리소스·멤버십이 없음 |

## 7. PHOTO-06 공유집(앨범) 사진 일괄 삭제

같은 공유집(앨범)에 속한 사진 중 요청 사용자가 업로드했거나, 업로더가 탈퇴한 사용자인 경우 요청 사용자가 상위 공유 그룹 방장인 사진만 한 번에 최대 100개 soft delete한다.
PHOTO-05와 마찬가지로 사진 원본을 삭제하므로 다른 공유집(앨범)에서도 함께 접근이 차단되며, 복구할 수 없다.
모든 사진을 검증한 뒤 하나의 트랜잭션으로 처리하며 일부 성공은 허용하지 않는다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Idempotency-Key` | UUID String | O | 일괄 삭제 재시도 식별자 | `54cf8d7e-a23e-4e76-90f7-603f122b1507` |

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedAlbumId` | UUID | O | 사진을 일괄 삭제할 공유집(앨범) 식별자 |

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

### Success Response

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

### Fail Response

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_PHOTO_IDS` | 배열이 비었거나 중복·형식 오류·100개 초과 |
| 403 | `NOT_PHOTO_UPLOADER` | 대상 중 요청 사용자가 업로드하지 않았고, 탈퇴한 업로더의 공유 그룹 방장 권한으로도 삭제할 수 없는 사진이 있음 |
| 404 | `SHARED_ALBUM_NOT_FOUND` | 공유집(앨범) 또는 활성 멤버십이 없음 |
| 404 | `PHOTO_NOT_FOUND` | 대상 중 접근 가능한 활성 사진이 아닌 항목이 있음 |

## 8. PHOTO-07 공유집(앨범)에 기존 사진 추가

같은 공유 그룹에 속한 기존 사진을 요청 경로의 공유집(앨범)에 추가로 담는다.
이미 해당 공유집(앨범)에 속한 사진은 건너뛰고 새로 추가한 매핑 수만 반환하는 멱등 동작이다.
사진 원본을 새로 만들지 않고 `shared_album_photo` 행만 추가한다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Idempotency-Key` | UUID String | O | 추가 재시도 식별자 | `54cf8d7e-a23e-4e76-90f7-603f122b1507` |

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedAlbumId` | UUID | O | 사진을 추가할 공유집(앨범) 식별자 |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---:|---|---|
| `photoIds` | UUID[] | O | 중복 없이 1~100개 | 추가할 사진 식별자 목록 |

```json
{
  "photoIds": [
    "385ff765-b20c-49a2-8e62-e1457784aa15",
    "4c9410ec-1dfa-4dbb-b8e6-2a4e947256c9"
  ]
}
```

### Success Response

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "PHOTOS_ATTACHED",
  "message": "공유집(앨범)에 사진을 추가했습니다.",
  "data": {
    "attachedCount": 1,
    "alreadyAttachedCount": 1
  }
}
```

### Fail Response

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_PHOTO_IDS` | 배열이 비었거나 중복·형식 오류·100개 초과 |
| 404 | `SHARED_ALBUM_NOT_FOUND` | 공유집(앨범) 또는 활성 멤버십이 없음 |
| 404 | `PHOTO_NOT_FOUND` | 대상 중 접근 가능한 활성 사진이 아닌 항목이 있음 |
| 409 | `PHOTO_NOT_IN_SAME_SHARED_GROUP` | 대상 중 공유집(앨범)과 다른 공유 그룹에 속한 사진이 있음 |

## 9. PHOTO-08 공유집(앨범)에서 사진 제거

요청 경로의 공유집(앨범)에서 사진을 뺀다. 해당 `shared_album_photo` 매핑을 즉시 물리 삭제한다.
해당 공유집(앨범)에 속해 있지 않은 사진은 건너뛰는 멱등 동작이다.
사진이 다른 공유집(앨범)에도 속해 있으면 원본과 그 소속은 그대로 유지된다.
**사진은 항상 1개 이상의 공유집(앨범)에 속해야 하므로, 제거 대상 공유집(앨범)이 그 사진의 마지막 소속이면 매핑 제거와 함께 사진 원본도 soft delete된다.** 이 경우 사진은 모든 곳에서 접근이 차단되며, 특정 앨범에서만 빼려던 의도와 다를 수 있으므로 iOS는 제거 전 대상 사진이 다른 공유집(앨범)에도 속해 있는지 확인해 안내하는 것을 권장한다.

### Request

#### Header

| 필드 | 타입 | 필수 | 설명 | 예시 |
|---|---|---:|---|---|
| `Idempotency-Key` | UUID String | O | 제거 재시도 식별자 | `54cf8d7e-a23e-4e76-90f7-603f122b1507` |

#### Path

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sharedAlbumId` | UUID | O | 사진을 제거할 공유집(앨범) 식별자 |

#### Body

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---:|---|---|
| `photoIds` | UUID[] | O | 중복 없이 1~100개 | 제거할 사진 식별자 목록 |

```json
{
  "photoIds": [
    "385ff765-b20c-49a2-8e62-e1457784aa15"
  ]
}
```

### Success Response

#### HTTP Status Code: `200 OK`

```json
{
  "status": 200,
  "code": "PHOTOS_DETACHED",
  "message": "공유집(앨범)에서 사진을 제거했습니다.",
  "data": {
    "detachedCount": 1,
    "deletedPhotoCount": 0
  }
}
```

`detachedCount`는 물리 삭제한 `shared_album_photo` 매핑 수, `deletedPhotoCount`는 그중 마지막 소속을 잃어 함께 soft delete된 사진 수다.

### Fail Response

| HTTP Status | code | 조건 |
|---:|---|---|
| 400 | `INVALID_PHOTO_IDS` | 배열이 비었거나 중복·형식 오류·100개 초과 |
| 404 | `SHARED_ALBUM_NOT_FOUND` | 공유집(앨범) 또는 활성 멤버십이 없음 |
