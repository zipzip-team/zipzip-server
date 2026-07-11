# Zipzip 데이터 사전

## 1. 문서 목적

이 문서는 Zipzip 1차 서버 데이터 모델의 테이블, 컬럼, 제약 조건, 인덱스, 권한 기준을 설명한다.
기준 모델은 `04-data-modeling.md`와 `dbml/zipzip.dbml`이다.

## 2. 테이블 목록

| 테이블 | 한글명 | 설명 |
|---|---|---|
| `app_user` | 사용자 | Apple 로그인 후 서버에 등록된 사용자 |
| `refresh_token` | Refresh Token | Refresh Token 해시와 회전 상태 |
| `invite_code_reservation` | 초대 코드 예약 원장 | 발급된 초대 코드의 점유 예약 테이블 |
| `shared_group` | 공유 그룹 | 초대 코드, 멤버십, 채팅, 공유집(앨범)을 묶는 최상위 공유 공간 |
| `shared_group_membership` | 공유 그룹 멤버십 | 사용자와 공유 그룹의 참여 관계 및 역할 |
| `shared_group_chat_message` | 그룹 채팅 메시지 | 공유 그룹 채팅 타임라인에 포함되는 일반 채팅 메시지 |
| `shared_album` | 공유집(앨범) | 공유 그룹 안에서 사진을 담는 단위 |
| `photo_upload_reservation` | 사진 업로드 예약 원장 | 발급된 업로드 `objectKey`의 발급 대상(사용자·공유집(앨범)) 점유 예약 테이블 |
| `photo` | 사진 | 공유 그룹에 업로드된 사진 원본의 Object Storage 참조 |
| `shared_album_photo` | 앨범-사진 매핑 | 사진과 공유집(앨범)의 N:M 소속 관계 |
| `photo_like` | 사진 좋아요 | 사용자가 사진에 좋아요를 누른 상태 |
| `photo_comment` | 사진 댓글 | 단일 사진에 달리며 해당 공유 그룹 채팅 타임라인에도 포함되는 댓글 |

### 2.1 Java 타입 매핑

| DB 타입 | Java/JPA 타입 | API JSON 표현 |
|---|---|---|
| `uuid` | `java.util.UUID` | UUID 문자열 |
| `timestamptz` | `java.time.Instant` | UTC ISO-8601 문자열. 예: `2026-07-03T10:15:30Z` |

날짜/시간 컬럼은 서버 기본 시간대의 영향을 받지 않도록 `LocalDateTime`이 아니라 `Instant`로 매핑한다.

## 3. 테이블 상세

### 3.1 `app_user`

Apple 로그인 후 서버에 등록된 사용자이다.
탈퇴 시 `deleted_at`을 기록하고, 공유 콘텐츠에서 사용자 표시는 "탈퇴한 사용자"로 대체한다.
탈퇴 시 `display_name`은 "탈퇴한 사용자"로 갱신하고, 사용자 행은 재가입 복구를 위해 물리 삭제하지 않는다.
동일한 Apple 계정으로 재가입하면 기존 행의 `deleted_at`을 해제한다.

| 컬럼 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | `uuid` | O | 사용자 식별자 |
| `apple_subject` | `varchar(255)` | O | Apple 로그인 사용자 식별자 |
| `display_name` | `varchar(50)` | O | 화면 표시 이름 |
| `created_at` | `timestamptz` | O | 생성 시각 |
| `updated_at` | `timestamptz` | O | 수정 시각 |
| `deleted_at` | `timestamptz` | X | 탈퇴 시각 |

주요 제약:

- `apple_subject` unique
- `display_name` 공백 불가
- 탈퇴 시 `display_name`을 "탈퇴한 사용자"로 갱신

### 3.2 `refresh_token`

Refresh Token 원문을 저장하지 않고 해시와 회전 상태만 저장한다.

| 컬럼 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | `uuid` | O | Refresh Token 행 식별자 |
| `app_user_id` | `uuid` | O | 토큰 소유 사용자 |
| `token_hash` | `varchar(255)` | O | Refresh Token 원문 해시 |
| `token_family_id` | `uuid` | O | 로그인 세션별 토큰 계열 |
| `expires_at` | `timestamptz` | O | 만료 시각 |
| `revoked_at` | `timestamptz` | X | 폐기 시각 |
| `replaced_by_refresh_token_id` | `uuid` | X | 회전 후 다음 Refresh Token |
| `created_at` | `timestamptz` | O | 생성 시각 |
| `updated_at` | `timestamptz` | O | 수정 시각 |

주요 제약:

- `token_hash` unique
- `replaced_by_refresh_token_id` unique
- `expires_at > created_at`
- `revoked_at is null or revoked_at >= created_at`

### 3.3 `invite_code_reservation`

공유 그룹 초대 코드의 중복 점유를 방지하는 예약 테이블이다.

| 컬럼 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `invite_code` | `varchar(64)` | O | 예약된 초대 코드이자 PK |
| `created_at` | `timestamptz` | O | 최초 예약 시각 |

주요 제약:

- `invite_code` 공백 불가
- 공유 그룹 물리 삭제 시 행도 물리 삭제

### 3.4 `shared_group`

공유 탭에 표시되는 최상위 공유 공간이다.

| 컬럼 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | `uuid` | O | 공유 그룹 식별자 |
| `created_by_app_user_id` | `uuid` | O | 최초 생성 사용자 |
| `name` | `varchar(100)` | O | 공유 그룹 이름 |
| `invite_code` | `varchar(64)` | O | 공유 그룹 참여 코드 |
| `created_at` | `timestamptz` | O | 생성 시각 |
| `updated_at` | `timestamptz` | O | 수정 시각 |
| `deleted_at` | `timestamptz` | X | 삭제 시각 |

주요 제약:

- `invite_code` unique
- `invite_code`는 `invite_code_reservation.invite_code` 참조
- `name`, `invite_code` 공백 불가
- 공유 그룹 정보 수정과 삭제는 `HOST`만 가능

### 3.5 `shared_group_membership`

사용자가 어떤 공유 그룹에 어떤 역할로 참여하는지 저장한다.

| 컬럼 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | `uuid` | O | 멤버십 식별자 |
| `shared_group_id` | `uuid` | O | 참여한 공유 그룹 |
| `app_user_id` | `uuid` | O | 참여 사용자 |
| `role` | `shared_group_role` | O | `HOST` 또는 `MEMBER` |
| `created_at` | `timestamptz` | O | 참여 시각 |
| `updated_at` | `timestamptz` | O | 수정 시각 |

주요 제약:

- `shared_group_id`, `app_user_id` unique
- PostgreSQL 보완 SQL에서 공유 그룹별 `HOST` 한 명 partial unique 적용
- 멤버 나가기 시 `MEMBER` 행은 물리 삭제

### 3.6 `shared_group_chat_message`

공유 그룹 안에서 사진 컨텍스트 없이 작성하는 일반 채팅 메시지이다. 공유 그룹 하나가 하나의 채팅방이며, 이 테이블의 메시지는 사진 댓글과 병합한 채팅 타임라인에 표시된다.
1차 구현은 폴링으로 새 메시지를 조회한다.

| 컬럼 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | `uuid` | O | 메시지 식별자 |
| `shared_group_id` | `uuid` | O | 메시지가 속한 공유 그룹 |
| `app_user_id` | `uuid` | O | 작성 사용자 |
| `content` | `varchar(1000)` | O | 메시지 본문 |
| `created_at` | `timestamptz` | O | 작성 시각 |
| `updated_at` | `timestamptz` | O | 수정 시각 |

주요 제약:

- `content` 공백 불가
- 메시지 작성과 조회는 활성 공유 그룹 멤버십 필요
- MVP는 메시지 작성·조회만 제공하며, 수정·삭제는 후속 범위
- 일반 메시지 구간은 `created_at`, `id` 기준으로 조회하며, 최종 채팅 타임라인 커서는 `created_at`, 항목 타입, 항목 ID를 함께 사용

### 3.7 `shared_album`

공유 그룹 안에서 사진을 담는 공유집(앨범)이다.
기존 `shared_house` 물리 테이블의 역할은 이 테이블로 통합한다.

| 컬럼 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | `uuid` | O | 공유집(앨범) 식별자 |
| `shared_group_id` | `uuid` | O | 상위 공유 그룹 |
| `created_by_app_user_id` | `uuid` | O | 생성 사용자 |
| `name` | `varchar(100)` | O | 공유집(앨범) 이름 |
| `created_at` | `timestamptz` | O | 생성 시각 |
| `updated_at` | `timestamptz` | O | 수정 시각 |
| `deleted_at` | `timestamptz` | X | 삭제 시각 |

주요 제약:

- `name` 공백 불가
- 생성과 수정은 활성 공유 그룹 멤버만 가능
- 삭제는 생성자 또는 탈퇴한 생성자의 공유 그룹 방장만 가능

### 3.8 `photo_upload_reservation`

PHOTO-02에서 발급한 업로드 `objectKey`의 발급 대상 점유를 예약하는 테이블이다.
PHOTO-03 완료 등록은 이 테이블에서 요청 사용자·요청 경로 공유집(앨범)과 일치하고 만료되지 않은 행을 찾아야만 진행하며, 성공하면 해당 행을 물리 삭제한다.

| 컬럼 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `object_key` | `varchar(500)` | O | 예약된 업로드 객체 키이자 PK |
| `shared_album_id` | `uuid` | O | 발급 대상 공유집(앨범) |
| `requested_by_app_user_id` | `uuid` | O | 발급을 요청한 사용자 |
| `expires_at` | `timestamptz` | O | 예약 만료 시각. presigned PUT URL 만료 시각과 맞춘다 |
| `created_at` | `timestamptz` | O | 발급 시각 |

주요 제약:

- 완료 등록 성공 시 행을 즉시 물리 삭제(재사용 방지)
- 만료된 미완료 행은 정기 스윕이 물리 삭제하고, 대응하는 Object Storage 객체가 있으면 함께 정리
- 상위 `shared_album` 삭제 시 cascade로 함께 삭제

### 3.9 `photo`

사진 원본의 파일 참조 정보를 저장한다.
사진은 공유 그룹에 직접 속하지 않고, `shared_album_photo`를 통해서만 하나 이상의 공유집(앨범)에 속한다(공유 위계는 공유 그룹 > 공유집(앨범) > 사진).

| 컬럼 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | `uuid` | O | 사진 식별자 |
| `uploaded_by_app_user_id` | `uuid` | O | 업로드 사용자 |
| `device_model` | `varchar(100)` | X | iOS가 EXIF에서 추출해 전달한 촬영 기기명 |
| `original_object_key` | `varchar(500)` | O | OCI Object Storage 원본 이미지 객체 키. API는 조회 시점에 이 키로 presigned GET URL을 발급 |
| `thumbnail_object_key` | `varchar(500)` | X | 썸네일 이미지 객체 키. 비동기 생성 전에는 `null` |
| `thumbnail_status` | `varchar(20)` | O | 썸네일 생성 상태. `PENDING`, `READY`, `FAILED` |
| `taken_at` | `timestamptz` | X | EXIF에서 추출했거나 보정한 촬영일시 |
| `latitude` | `double precision` | X | 촬영 위치 위도 |
| `longitude` | `double precision` | X | 촬영 위치 경도 |
| `location_name` | `varchar(200)` | X | 촬영 위치명 |
| `is_inferred` | `boolean` | O | 위치정보 추론 여부. 기본값 `false`. iOS 메타데이터 제안 UI와 연동되는 핵심 필드 |
| `width` | `int` | X | 이미지 가로 픽셀 |
| `height` | `int` | X | 이미지 세로 픽셀 |
| `created_at` | `timestamptz` | O | 등록 시각 |
| `updated_at` | `timestamptz` | O | 수정 시각 |
| `deleted_at` | `timestamptz` | X | 삭제 시각 |

주요 제약:

- `original_object_key` unique
- `thumbnail_status`는 `PENDING`, `READY`, `FAILED`만 허용(`chk_photo__thumbnail_status`)
- 원본 수정과 삭제는 원칙적으로 업로더만 가능
- 업로더가 탈퇴한 경우 삭제는 공유 그룹 방장이 가능
- 항상 1개 이상의 `shared_album_photo` 매핑을 가져야 한다(서비스 계층에서 강제)
- 업로드 완료 등록 시 `thumbnail_status`는 `PENDING`으로 시작하고, 백그라운드 썸네일 생성 결과에 따라 `READY` 또는 `FAILED`로 갱신

### 3.10 `shared_album_photo`

사진과 공유집(앨범)의 N:M 소속 관계를 저장한다.

| 컬럼 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | `uuid` | O | 매핑 식별자 |
| `shared_album_id` | `uuid` | O | 공유집(앨범) |
| `photo_id` | `uuid` | O | 사진 |
| `created_at` | `timestamptz` | O | 사진이 공유집(앨범)에 추가된 시각 |
| `updated_at` | `timestamptz` | O | 수정 시각 |

주요 제약:

- `shared_album_id`, `photo_id` unique
- 매핑 삭제(공유집(앨범) 삭제, 사진 제거)로 어떤 사진의 매핑이 0개가 되면 그 사진도 함께 soft delete한다
- 같은 사진을 같은 공유집(앨범)에 중복으로 담을 수 없음

### 3.11 `photo_like`

사용자가 특정 사진에 좋아요를 누른 상태이다.

| 컬럼 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | `uuid` | O | 좋아요 식별자 |
| `photo_id` | `uuid` | O | 좋아요 대상 사진 |
| `app_user_id` | `uuid` | O | 좋아요를 누른 사용자 |
| `created_at` | `timestamptz` | O | 생성 시각 |
| `updated_at` | `timestamptz` | O | 수정 시각 |

주요 제약:

- `photo_id`, `app_user_id` unique
- 좋아요 취소 또는 사용자 탈퇴 시 행을 물리 삭제

### 3.12 `photo_comment`

단일 사진에 달리는 댓글이다.

| 컬럼 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | `uuid` | O | 댓글 식별자 |
| `photo_id` | `uuid` | O | 댓글 대상 사진 |
| `app_user_id` | `uuid` | O | 작성 사용자 |
| `content` | `varchar(1000)` | O | 댓글 본문 |
| `created_at` | `timestamptz` | O | 작성 시각 |
| `updated_at` | `timestamptz` | O | 수정 시각 |

주요 제약:

- `content` 공백 불가
- 댓글 작성과 조회는 활성 공유 그룹 멤버십 필요

## 4. 외래 키

| 제약명 | 관계 | 삭제 규칙 |
|---|---|---|
| `fk_refresh_token__app_user` | `refresh_token.app_user_id` -> `app_user.id` | cascade |
| `fk_refresh_token__replaced_by` | `refresh_token.replaced_by_refresh_token_id` -> `refresh_token.id` | set null |
| `fk_shared_group__invite_code_reservation` | `shared_group.invite_code` -> `invite_code_reservation.invite_code` | restrict |
| `fk_shared_group__created_by_app_user` | `shared_group.created_by_app_user_id` -> `app_user.id` | restrict |
| `fk_shared_group_membership__shared_group` | `shared_group_membership.shared_group_id` -> `shared_group.id` | cascade |
| `fk_shared_group_membership__app_user` | `shared_group_membership.app_user_id` -> `app_user.id` | restrict |
| `fk_shared_group_chat_message__shared_group` | `shared_group_chat_message.shared_group_id` -> `shared_group.id` | cascade |
| `fk_shared_group_chat_message__app_user` | `shared_group_chat_message.app_user_id` -> `app_user.id` | restrict |
| `fk_shared_album__shared_group` | `shared_album.shared_group_id` -> `shared_group.id` | cascade |
| `fk_shared_album__created_by_app_user` | `shared_album.created_by_app_user_id` -> `app_user.id` | restrict |
| `fk_photo_upload_reservation__shared_album` | `photo_upload_reservation.shared_album_id` -> `shared_album.id` | cascade |
| `fk_photo_upload_reservation__requested_by_app_user` | `photo_upload_reservation.requested_by_app_user_id` -> `app_user.id` | restrict |
| `fk_photo__uploaded_by_app_user` | `photo.uploaded_by_app_user_id` -> `app_user.id` | restrict |
| `fk_shared_album_photo__shared_album` | `shared_album_photo.shared_album_id` -> `shared_album.id` | cascade |
| `fk_shared_album_photo__photo` | `shared_album_photo.photo_id` -> `photo.id` | cascade |
| `fk_photo_like__photo` | `photo_like.photo_id` -> `photo.id` | cascade |
| `fk_photo_like__app_user` | `photo_like.app_user_id` -> `app_user.id` | cascade |
| `fk_photo_comment__photo` | `photo_comment.photo_id` -> `photo.id` | cascade |
| `fk_photo_comment__app_user` | `photo_comment.app_user_id` -> `app_user.id` | restrict |

## 5. 주요 인덱스

| 테이블 | 인덱스 | 목적 |
|---|---|---|
| `shared_group_membership` | `uk_shared_group_membership__group_user` | 사용자 중복 참여 방지 |
| `shared_group_membership` | `uk_shared_group_membership__host` | 공유 그룹별 방장 1명 보장 |
| `shared_group_chat_message` | `idx_shared_group_chat_message__group_created_id` | 공유 그룹 일반 메시지 구간 조회 |
| `shared_album` | `idx_shared_album__shared_group_id_deleted_at_created_at` | 공유 그룹별 공유집(앨범) 목록 |
| `photo` | `idx_photo__active_display_at` | 촬영일 우선 사진 정렬 |
| `shared_album_photo` | `uk_shared_album_photo__shared_album_id_photo_id` | 앨범-사진 중복 매핑 방지 |
| `shared_album_photo` | `idx_shared_album_photo__shared_album_id_created_at` | 공유집(앨범)별 사진 목록 |
| `photo_like` | `uk_photo_like__photo_id_app_user_id` | 중복 좋아요 방지 |
| `photo_comment` | `idx_photo_comment__photo_id_created_at` | 사진별 댓글 목록 및 채팅 타임라인 병합 조회 |
| `photo_upload_reservation` | `idx_photo_upload_reservation__shared_album_id` | 공유집(앨범)별 예약 조회 |
| `photo_upload_reservation` | `idx_photo_upload_reservation__requested_by_app_user_id` | 사용자별 예약 조회 |
| `photo_upload_reservation` | `idx_photo_upload_reservation__expires_at` | 만료된 미완료 예약 스윕 |

## 6. 권한 기준

활성 멤버십은 멤버십 행이 존재하고, 상위 공유 그룹과 사용자가 모두 soft delete되지 않은 상태를 의미한다.

| 행위 | 권한 기준 |
|---|---|
| 공유 그룹 조회 | 요청 사용자의 활성 `shared_group_membership` 확인 |
| 공유 그룹 정보 수정 | 요청 사용자의 해당 공유 그룹 `HOST` 멤버십 확인 |
| 공유 그룹 삭제 | 요청 사용자의 해당 공유 그룹 `HOST` 멤버십 확인 |
| 공유 그룹 나가기 | 요청 사용자의 `MEMBER` 멤버십 물리 삭제 |
| 공유집(앨범) 생성 | 요청 사용자의 상위 공유 그룹 활성 멤버십 확인 |
| 공유집(앨범) 정보 수정 | 요청 사용자의 상위 공유 그룹 활성 멤버십 확인 |
| 공유집(앨범) 삭제 | 생성자는 활성 멤버십과 `shared_album.created_by_app_user_id` 일치 확인. 생성자가 탈퇴한 경우 공유 그룹 `HOST` 멤버십 확인 |
| 사진 업로드 URL 발급 | 대상 공유집(앨범)의 활성 상태와 요청 사용자의 상위 공유 그룹 활성 멤버십 확인. 발급한 각 `objectKey`를 요청 사용자·공유집(앨범)과 함께 `photo_upload_reservation`에 기록 |
| 사진 업로드 완료 등록 | `objectKey`에 대응하는 `photo_upload_reservation`이 요청 사용자·요청 경로 공유집(앨범)과 일치하고 만료되지 않았는지 확인. 통과하면 `photo`와 최초 `shared_album_photo` 매핑을 함께 생성하고 예약 행을 삭제 |
| 사진을 다른 공유집(앨범)에 추가·제거 | 대상 공유집(앨범)이 사진의 기존 공유집(앨범)과 같은 공유 그룹에 속하는지(사진의 `shared_album_photo` 매핑을 조인해 확인), 요청 사용자의 활성 멤버십이 있는지 확인. 제거가 사진의 마지막 매핑이면 사진도 함께 soft delete |
| 사진 원본 수정 | 활성 멤버십과 `photo.uploaded_by_app_user_id` 일치 확인 |
| 사진 원본 삭제 | 업로더는 활성 멤버십과 `photo.uploaded_by_app_user_id` 일치 확인. 업로더가 탈퇴한 경우 공유 그룹 `HOST` 멤버십 확인 |
| 사진 좋아요 생성·취소 | 요청 사용자의 활성 멤버십 확인. 취소는 본인 좋아요만 허용 |
| 사진 댓글 작성 | 요청 사용자의 활성 멤버십 확인 |
| 그룹 채팅 타임라인 조회 | 요청 사용자의 해당 공유 그룹 활성 멤버십 확인. 일반 메시지와 활성 사진 댓글을 병합하고, 사진의 공유 그룹 소속은 `shared_album_photo`와 `shared_album` 조인으로 확인 |
| 그룹 채팅 메시지 작성 | 요청 사용자의 해당 공유 그룹 활성 멤버십 확인 |

## 7. 삭제 정리 순서

1. 공유 그룹 삭제, 공유집(앨범) 삭제, 사진 삭제는 각 테이블의 `deleted_at` 기록으로 처리한다.
2. 사진 좋아요 취소는 해당 행을 즉시 물리 삭제한다. MVP에는 그룹 채팅 메시지와 사진 댓글의 사용자 수정·삭제 API가 없다.
3. 공유집(앨범) 삭제와 사진의 공유집(앨범) 제거 시점에는 관련 `shared_album_photo` 매핑을 즉시 물리 삭제하고, 매핑이 0개가 된 사진은 함께 soft delete한다. 공유 그룹 삭제 시에는 하위 공유집(앨범)·사진만 함께 soft delete하고, 30일 물리 정리 대상 식별을 위해 해당 매핑은 유지한다.
4. 사용자 탈퇴 시 활성 Refresh Token을 폐기하고 `app_user.deleted_at`을 기록한다.
5. 사용자 탈퇴 시 `app_user.display_name`을 "탈퇴한 사용자"로 갱신하고 `app_user` 행은 물리 삭제하지 않는다.
6. 사용자 탈퇴 시 방장으로 만든 공유 그룹은 soft delete하고(위 3번 절차를 그대로 따른다), `MEMBER`로 참여 중인 공유 그룹 멤버십은 물리 삭제한다.
7. 사용자 탈퇴 시 사진 좋아요는 물리 삭제한다.
8. 탈퇴한 사용자가 기존에 생성·작성·업로드한 공유 콘텐츠는 즉시 삭제하지 않고 사용자 표시는 "탈퇴한 사용자"로 대체한다.
9. 개별 삭제하거나 캐스케이드로 soft delete된 사진은 30일 뒤 Object Storage 원본·썸네일 객체 삭제가 끝난 뒤 `photo` 행을 물리 삭제한다.
10. 개별 삭제한 공유집(앨범)은 30일 뒤 `shared_album` 행을 물리 삭제한다. 관련 `shared_album_photo` 매핑은 3번에서 이미 정리되어 있다.
11. 공유 그룹은 30일 뒤 매핑으로 찾은 사진의 Object Storage 원본·썸네일을 먼저 삭제하고, 성공한 사진의 매핑·댓글·좋아요·사진 행을 물리 삭제한다. 실패하면 그룹과 초대 코드 예약을 유지해 재시도한다.
12. 모든 사진 정리 성공 후 공유 그룹은 남아 있는 멤버십·채팅 메시지·공유집(앨범)을 FK cascade로 정리하며 `shared_group` 행을 물리 삭제한다.
13. 공유 그룹 물리 삭제 트랜잭션에서 해당 초대 코드 예약 원장 행도 삭제한다.
14. 사진 업로드 완료 등록 트랜잭션에서 사용한 `photo_upload_reservation` 행을 물리 삭제한다.
15. 만료되었지만 완료 등록에 쓰이지 않은 `photo_upload_reservation` 행은 정기 스윕이 물리 삭제하고, 대응하는 Object Storage 객체가 남아 있으면 함께 정리한다.
