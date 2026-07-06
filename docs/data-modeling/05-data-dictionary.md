# Zipzip 데이터 사전

## 1. 문서 목적

이 문서는 Zipzip 1차 공유 폴더 서버 모델의 최종 테이블·컬럼 정의서이다.
개발, API 설계, 마이그레이션, 테스트에서 동일한 데이터 의미와 제약을 사용하기 위한 기준으로 삼는다.

기준 산출물:

- 논리 모델과 일반 제약: `dbml/zipzip.dbml`
- PostgreSQL partial/expression index: `dbml/postgresql-overrides.sql`
- 설계 근거: `decisions/data-model-decision-log.md`

DBML과 이 문서가 충돌하면 먼저 두 문서를 동기화한 뒤 구현한다. PostgreSQL 물리 스키마에는 DBML과 보완 SQL의 내용을 모두 반영해야 한다.

## 2. 표기 규칙

| 표기 | 의미 |
|---|---|
| PK | 기본 키 |
| FK | 외래 키 |
| UK | Unique 제약 또는 Unique index |
| NN | `not null` |
| CK | Check 제약 |
| O | 필수 값 |
| X | nullable 값 |
| 애플리케이션 필드 | Java/Kotlin 및 API 모델에서 사용하는 `camelCase` 대응명. 실제 API 노출 여부는 API 명세에서 결정한다. |

공통 규칙:

- 주요 업무 테이블의 `id`는 애플리케이션에서 생성하는 `uuid`다.
- `invite_code_reservation`은 불변 예약 원장이므로 `invite_code`를 PK로 사용하는 예외다.
- 모든 시각은 PostgreSQL `timestamptz`로 UTC 저장한다.
- `created_at`, `updated_at`의 DB 기본값은 `now()`이며 `updated_at`은 데이터 변경 시 애플리케이션이 갱신한다.
- `invite_code_reservation`과 `shared_album_photo`는 상태 변경을 기록하지 않으므로 `updated_at`을 두지 않는다.
- `deleted_at is null`이면 활성 데이터, `deleted_at is not null`이면 soft delete 데이터다.
- 문자열 길이는 문자 수 기준의 최대 허용 길이다.

## 3. 테이블 목록

| 도메인 | 테이블 | 한글명 | 생명주기 |
|---|---|---|---|
| 인증 | `app_user` | 사용자 | soft delete, 동일 Apple 계정 재가입 시 복구 |
| 인증 | `refresh_token` | Refresh Token | 만료·폐기 상태 관리 후 배치 물리 삭제 |
| 공유 폴더 | `invite_code_reservation` | 초대 코드 예약 원장 | 영구 보존 |
| 공유 폴더 | `shared_folder` | 공유 폴더 | soft delete 후 30일 뒤 물리 삭제 |
| 공유 폴더 | `shared_folder_membership` | 공유 폴더 멤버십 | 나가기·서비스 탈퇴 시 `MEMBER` 행 물리 삭제, 재참여 시 새 행 생성 |
| 공유 폴더 | `device` | 기기 태그 | soft delete |
| 사진 | `shared_album` | 앨범 | soft delete 후 30일 뒤 물리 삭제 |
| 사진 | `shared_album_photo` | 앨범 사진 포함 관계 | 앨범에서만 제거 시 즉시 물리 삭제 |
| 사진 | `photo` | 사진 | soft delete 후 Object Storage 객체와 함께 30일 뒤 물리 삭제 |
| 사진 | `photo_like` | 사진 좋아요 | 취소 시 즉시 물리 삭제 |
| 사진 | `photo_comment` | 사진 댓글 | soft delete, 사진 물리 삭제 시 종속 삭제 |

채팅 뷰는 별도 테이블이 아니라 공유 폴더 안의 `photo_comment`를 모아 보여주는 조회 모델이다.

## 4. 코드값 사전

### 4.1 `shared_folder_role`

| 코드값 | 한글명 | 정의 | 주요 권한 |
|---|---|---|---|
| `HOST` | 방장 | 공유 폴더를 만든 사용자의 역할 | 공유 폴더 삭제 가능, 강제 퇴장 불가 |
| `MEMBER` | 멤버 | 초대 코드로 공유 폴더에 참여한 사용자의 역할 | 공유 폴더 삭제 불가, 직접 나가기 가능 |

방장만 공유 폴더 정보를 수정하거나 공유 폴더를 삭제할 수 있다. 방장과 멤버 모두 앨범 생성·수정, 사진 업로드, 앨범 사진 추가·제거, 좋아요, 댓글 작성을 할 수 있다. 앨범 삭제는 앨범 생성자만, 사진 원본 수정·삭제는 사진 업로더만 할 수 있다.

## 5. 테이블·컬럼 사전

### 5.1 `app_user` — 사용자

Apple 로그인 후 서버에 등록된 사용자를 저장한다. 동일한 Apple 계정으로 재가입하면 새 행을 만들지 않고 기존 행의 `deleted_at`을 해제한다.

| 컬럼 | 애플리케이션 필드 | 한글명 | 타입 | 필수 | 기본값 | 키·제약 | 정의 |
|---|---|---|---|---|---|---|---|
| `id` | `id` | 사용자 식별자 | `uuid` | O | - | PK | 애플리케이션에서 생성하는 사용자 식별자 |
| `apple_subject` | `appleSubject` | Apple 사용자 식별자 | `varchar(255)` | O | - | UK | Apple 로그인으로 받은 변경되지 않는 식별자. 토큰 원문이 아니다. |
| `display_name` | `displayName` | 표시 이름 | `varchar(50)` | O | - | CK: 공백 불가 | 참여자와 콘텐츠 작성자 표시에 사용하는 이름 |
| `created_at` | `createdAt` | 등록일시 | `timestamptz` | O | `now()` | - | 사용자 최초 등록 시각 |
| `updated_at` | `updatedAt` | 수정일시 | `timestamptz` | O | `now()` | - | 사용자 정보 마지막 수정 시각 |
| `deleted_at` | `deletedAt` | 탈퇴일시 | `timestamptz` | X | `null` | - | 사용자 탈퇴 시각. null이면 활성 사용자 |

인덱스·추가 규칙:

- `idx_app_user__deleted_at(deleted_at)`
- Apple이 최초 로그인에서 이름을 제공하지 않으면 클라이언트에서 표시 이름을 입력받은 뒤 사용자 등록을 완료한다.
- 사용자 탈퇴 시 모든 활성 Refresh Token을 폐기하고 과거 공유 폴더 멤버십은 재가입 시 자동 복구하지 않는다.

### 5.2 `refresh_token` — Refresh Token

자체 발급 Refresh Token의 원문 대신 해시와 회전 생명주기를 저장한다. 로그인 세션마다 별도의 토큰 계열을 가지며 다중 세션을 허용한다.

| 컬럼 | 애플리케이션 필드 | 한글명 | 타입 | 필수 | 기본값 | 키·제약 | 정의 |
|---|---|---|---|---|---|---|---|
| `id` | `id` | Refresh Token 식별자 | `uuid` | O | - | PK | Refresh Token 행 식별자 |
| `app_user_id` | `appUserId` | 사용자 식별자 | `uuid` | O | - | FK → `app_user.id` | 토큰을 소유한 사용자 |
| `token_hash` | `tokenHash` | 토큰 해시 | `varchar(255)` | O | - | UK | Refresh Token 원문의 해시값. 외부 응답에 노출하지 않는다. |
| `token_family_id` | `tokenFamilyId` | 토큰 계열 식별자 | `uuid` | O | - | - | 같은 로그인 세션에서 회전된 토큰 계열 |
| `expires_at` | `expiresAt` | 만료일시 | `timestamptz` | O | - | CK: `created_at` 이후 | 토큰 만료 시각 |
| `revoked_at` | `revokedAt` | 폐기일시 | `timestamptz` | X | `null` | CK: `created_at` 이후 | 로그아웃, 회전 또는 재사용 탐지로 폐기한 시각 |
| `replaced_by_refresh_token_id` | `replacedByRefreshTokenId` | 다음 토큰 식별자 | `uuid` | X | `null` | UK, self FK | 회전 후 발급된 다음 Refresh Token 행 |
| `created_at` | `createdAt` | 발급일시 | `timestamptz` | O | `now()` | - | Refresh Token 발급 시각 |
| `updated_at` | `updatedAt` | 수정일시 | `timestamptz` | O | `now()` | - | 토큰 상태 마지막 수정 시각 |

인덱스·추가 규칙:

- `idx_refresh_token__app_user_id_token_family_id(app_user_id, token_family_id)`
- `idx_refresh_token__expires_at(expires_at)`
- `idx_refresh_token__revoked_at(revoked_at)`
- 회전은 기존 토큰 폐기, 새 토큰 생성, 다음 토큰 참조 기록을 하나의 트랜잭션에서 처리한다.
- 이미 폐기된 토큰이 다시 제시되면 같은 `token_family_id`의 토큰을 모두 폐기한다.

### 5.3 `invite_code_reservation` — 초대 코드 예약 원장

과거에 발급한 초대 코드를 영구 예약하여 공유 폴더가 물리 삭제된 뒤에도 코드 재사용을 막는다. 사용자에게 직접 노출하는 업무 이력 테이블이 아니다.

| 컬럼 | 애플리케이션 필드 | 한글명 | 타입 | 필수 | 기본값 | 키·제약 | 정의 |
|---|---|---|---|---|---|---|---|
| `invite_code` | `inviteCode` | 초대 코드 | `varchar(64)` | O | - | PK, CK: 공백 불가 | 과거 발급분을 포함해 영구 예약하는 코드 |
| `created_at` | `createdAt` | 예약일시 | `timestamptz` | O | `now()` | - | 코드를 최초로 예약한 시각 |

추가 규칙:

- 행을 수정하거나 삭제하지 않는다.
- 공유 폴더 생성 시 코드 예약부터 공유 폴더·방장 멤버십 생성까지 하나의 트랜잭션에서 처리한다.
- PK 충돌 시 새 무작위 코드를 생성하여 다시 시도한다.

### 5.4 `shared_folder` — 공유 폴더

공유 뷰에 카드로 표시되는 실제 공유 공간이며 초대, 멤버십, 관리, 앨범과 채팅 뷰의 조회 루트다.

| 컬럼 | 애플리케이션 필드 | 한글명 | 타입 | 필수 | 기본값 | 키·제약 | 정의 |
|---|---|---|---|---|---|---|---|
| `id` | `id` | 공유 폴더 식별자 | `uuid` | O | - | PK | 애플리케이션에서 생성하는 공유 폴더 식별자 |
| `created_by_app_user_id` | `createdByAppUserId` | 최초 생성 사용자 식별자 | `uuid` | O | - | FK → `app_user.id` | 공유 폴더를 최초 생성한 사용자. 현재 방장 권한의 직접 기준은 아니다. |
| `name` | `name` | 공유 폴더 이름 | `varchar(100)` | O | - | CK: 공백 불가 | 공유 폴더 목록과 내부 상단에 표시하는 이름 |
| `invite_code` | `inviteCode` | 초대 코드 | `varchar(64)` | O | - | UK, FK → `invite_code_reservation.invite_code`, CK: 공백 불가 | 생성 후 변경·만료·재발급하지 않는 참여 코드 |
| `created_at` | `createdAt` | 생성일시 | `timestamptz` | O | `now()` | - | 공유 폴더 생성 시각 |
| `updated_at` | `updatedAt` | 수정일시 | `timestamptz` | O | `now()` | - | 공유 폴더 정보 마지막 수정 시각 |
| `deleted_at` | `deletedAt` | 삭제일시 | `timestamptz` | X | `null` | - | 방장이 공유 폴더를 삭제한 시각. null이면 활성 공유 폴더 |

인덱스·추가 규칙:

- `idx_shared_folder__created_by_app_user_id_deleted_at(created_by_app_user_id, deleted_at)`
- `idx_shared_folder__deleted_at(deleted_at)`
- 현재 방장은 활성 `shared_folder_membership.role = 'HOST'`로 판단한다.
- 공유 폴더 이름 같은 공유 폴더 정보 수정과 삭제는 활성 방장만 할 수 있다.
- 사용자에게 복구 기능을 제공하지 않으며 30일 뒤 하위 데이터부터 물리 삭제한다. 초대 코드 예약 행은 남긴다.

### 5.5 `shared_folder_membership` — 공유 폴더 멤버십

사용자가 어떤 공유 폴더에 어떤 역할로 참여하는지 저장한다. 콘텐츠의 작성자 식별자가 아니라 공유 폴더 접근 권한 판단에 사용한다.

| 컬럼 | 애플리케이션 필드 | 한글명 | 타입 | 필수 | 기본값 | 키·제약 | 정의 |
|---|---|---|---|---|---|---|---|
| `id` | `id` | 멤버십 식별자 | `uuid` | O | - | PK | 공유 폴더 참여 관계 식별자 |
| `shared_folder_id` | `sharedFolderId` | 공유 폴더 식별자 | `uuid` | O | - | FK → `shared_folder.id` | 참여한 공유 폴더 |
| `app_user_id` | `appUserId` | 사용자 식별자 | `uuid` | O | - | FK → `app_user.id` | 공유 폴더에 참여한 사용자 |
| `role` | `role` | 공유 폴더 역할 | `shared_folder_role` | O | - | Enum | `HOST` 또는 `MEMBER` |
| `created_at` | `createdAt` | 참여일시 | `timestamptz` | O | `now()` | - | 공유 폴더에 참여한 시각 |
| `updated_at` | `updatedAt` | 수정일시 | `timestamptz` | O | `now()` | - | 멤버십 정보 마지막 수정 시각 |

인덱스·추가 규칙:

- `idx_shared_folder_membership__folder_created(shared_folder_id, created_at)`
- `idx_shared_folder_membership__user_created(app_user_id, created_at)`
- `uk_shared_folder_membership__folder_user(shared_folder_id, app_user_id)`
- `idx_shared_folder_membership__folder_role(shared_folder_id, role)`
- `uk_shared_folder_membership__host(shared_folder_id) where role = 'HOST'`
- 멤버 강제 퇴장은 지원하지 않는다. 멤버가 나가면 해당 행을 물리 삭제하고 재참여하면 새 `MEMBER` 행을 생성한다.
- 방장은 나갈 수 없으며 공유 폴더 삭제만 할 수 있다.
- 사용자가 Zipzip 서비스를 탈퇴하면 `MEMBER` 멤버십을 물리 삭제한다. 방장으로 만든 공유 폴더는 soft delete하고 해당 `HOST` 멤버십은 공유 폴더 물리 삭제 시 FK cascade로 함께 삭제한다.
- 멤버십 행이 존재하고 상위 공유 폴더가 soft delete되지 않은 경우에만 활성 멤버십으로 판단한다.

### 5.6 `device` — 기기 태그

사용자가 주로 촬영에 사용하는 기기의 표시용 태그를 저장한다. 물리 장비의 고유 인스턴스나 사진별 EXIF 엔티티가 아니다.

| 컬럼 | 애플리케이션 필드 | 한글명 | 타입 | 필수 | 기본값 | 키·제약 | 정의 |
|---|---|---|---|---|---|---|---|
| `id` | `id` | 기기 태그 식별자 | `uuid` | O | - | PK | 애플리케이션에서 생성하는 기기 태그 식별자 |
| `app_user_id` | `appUserId` | 사용자 식별자 | `uuid` | O | - | FK → `app_user.id` | 기기 태그를 소유한 사용자 |
| `name` | `name` | 기기명 | `varchar(100)` | O | - | CK: 공백 불가 | 화면에 표시하는 원본 기기 이름 |
| `created_at` | `createdAt` | 등록일시 | `timestamptz` | O | `now()` | - | 기기 태그 등록 시각 |
| `updated_at` | `updatedAt` | 수정일시 | `timestamptz` | O | `now()` | - | 기기 태그 마지막 수정 시각 |
| `deleted_at` | `deletedAt` | 삭제일시 | `timestamptz` | X | `null` | - | 기기 태그 삭제 시각. null이면 활성 태그 |

인덱스·추가 규칙:

- `idx_device__app_user_id_deleted_at_created_at(app_user_id, deleted_at, created_at)`
- `idx_device__user_name_deleted(app_user_id, name, deleted_at)`
- `uk_device__active_name(app_user_id, lower(btrim(name))) where deleted_at is null`
- 별도 `normalized_name` 컬럼을 두지 않고 PostgreSQL 표현식으로 공백과 대소문자를 정규화한다.
- 시리얼 번호와 UDID는 저장하지 않는다. 이미지 파일의 위치 정보와 전체 EXIF는 DB 컬럼으로 저장하지 않으며 Object Storage에 저장하는 원본 파일에서는 제거하지 않는다.

### 5.7 `shared_album` — 앨범

공유 폴더 참여자가 공유 폴더 안에서 사진을 묶는 단위다. 사진 없이도 존재할 수 있다.

| 컬럼 | 애플리케이션 필드 | 한글명 | 타입 | 필수 | 기본값 | 키·제약 | 정의 |
|---|---|---|---|---|---|---|---|
| `id` | `id` | 앨범 식별자 | `uuid` | O | - | PK | 애플리케이션에서 생성하는 앨범 식별자 |
| `shared_folder_id` | `sharedFolderId` | 공유 폴더 식별자 | `uuid` | O | - | FK → `shared_folder.id` | 앨범이 속한 공유 폴더 |
| `created_by_app_user_id` | `createdByAppUserId` | 생성 사용자 식별자 | `uuid` | O | - | FK → `app_user.id` | 앨범 생성자이며 삭제 권한의 기준 |
| `name` | `name` | 앨범 이름 | `varchar(100)` | O | - | CK: 공백 불가 | 공유 폴더 내부에 표시하는 이름 |
| `created_at` | `createdAt` | 생성일시 | `timestamptz` | O | `now()` | - | 앨범 생성 시각 |
| `updated_at` | `updatedAt` | 수정일시 | `timestamptz` | O | `now()` | - | 앨범 정보 마지막 수정 시각 |
| `deleted_at` | `deletedAt` | 삭제일시 | `timestamptz` | X | `null` | - | 앨범 삭제 시각. null이면 활성 앨범 |

인덱스·추가 규칙:

- `idx_shared_album__shared_folder_id_deleted_at_created_at(shared_folder_id, deleted_at, created_at)`
- `idx_shared_album__created_by_app_user_id_deleted_at(created_by_app_user_id, deleted_at)`
- `idx_shared_album__deleted_at(deleted_at)`
- 활성 방장과 멤버 모두 생성하고 이름 같은 앨범 정보를 수정할 수 있지만 삭제는 생성자만 할 수 있다.
- 삭제 후 30일이 지나고 앨범 사진 포함 관계가 정리되면 물리 삭제한다.

### 5.8 `shared_album_photo` — 앨범 사진 포함 관계

사진이 특정 앨범에 포함되어 있음을 저장한다. 한 사진은 같은 공유 폴더 안의 여러 앨범에 포함될 수 있다.

| 컬럼 | 애플리케이션 필드 | 한글명 | 타입 | 필수 | 기본값 | 키·제약 | 정의 |
|---|---|---|---|---|---|---|---|
| `id` | `id` | 앨범 사진 포함 관계 식별자 | `uuid` | O | - | PK | 앨범과 사진의 포함 관계 식별자 |
| `shared_album_id` | `sharedAlbumId` | 앨범 식별자 | `uuid` | O | - | FK → `shared_album.id` | 사진을 포함하는 앨범 |
| `photo_id` | `photoId` | 사진 식별자 | `uuid` | O | - | FK → `photo.id` | 앨범에 포함된 사진 |
| `added_by_app_user_id` | `addedByAppUserId` | 추가 사용자 식별자 | `uuid` | O | - | FK → `app_user.id` | 사진을 앨범에 추가한 사용자 |
| `created_at` | `createdAt` | 추가일시 | `timestamptz` | O | `now()` | - | 사진을 앨범에 추가한 시각 |

인덱스·추가 규칙:

- `uk_shared_album_photo__album_photo(shared_album_id, photo_id)`
- `idx_shared_album_photo__photo_album(photo_id, shared_album_id)`
- `idx_shared_album_photo__added_by_created_at(added_by_app_user_id, created_at)`
- 같은 사진을 같은 앨범에 중복 추가할 수 없다.
- `선택한 사진 공유앨범에 복사`는 대상 앨범에 `shared_album_photo` 행을 추가한다.
- `이 앨범에서만 제거`는 이 행을 물리 삭제하며 `photo`와 Object Storage 객체는 유지한다.
- 사진과 앨범은 같은 활성 공유 폴더에 속해야 하며 이 규칙은 서비스 계층에서 검증한다.

### 5.9 `photo` — 사진

공유 폴더에 업로드된 사진 원본의 OCI Object Storage 참조와 최소 파일 정보를 저장한다. 이미지 바이너리는 DB에 저장하지 않는다.

| 컬럼 | 애플리케이션 필드 | 한글명 | 타입 | 필수 | 기본값 | 키·제약 | 정의 |
|---|---|---|---|---|---|---|---|
| `id` | `id` | 사진 식별자 | `uuid` | O | - | PK | 애플리케이션에서 생성하는 사진 식별자 |
| `shared_folder_id` | `sharedFolderId` | 공유 폴더 식별자 | `uuid` | O | - | FK → `shared_folder.id` | 사진 원본이 속한 공유 폴더 |
| `uploaded_by_app_user_id` | `uploadedByAppUserId` | 업로드 사용자 식별자 | `uuid` | O | - | FK → `app_user.id` | 사진 업로더이며 원본 수정·삭제 권한의 기준 |
| `object_key` | `objectKey` | 객체 키 | `text` | O | - | UK | OCI Object Storage에서 이미지 객체를 찾는 고유 키 |
| `original_file_name` | `originalFileName` | 원본 파일명 | `varchar(255)` | O | - | CK: 공백 불가 | 업로드 당시 원본 파일명 |
| `content_type` | `contentType` | 미디어 타입 | `varchar(100)` | O | - | CK: `image/%` | 이미지 MIME type |
| `file_size` | `fileSize` | 파일 크기 | `bigint` | O | - | CK: 0 초과 | 이미지 파일 크기. byte 단위 |
| `taken_at` | `takenAt` | 촬영일시 | `timestamptz` | X | `null` | - | 서버가 업로드 이미지의 EXIF에서 추출한 촬영일시. EXIF 전체를 DB 컬럼으로 저장하지 않는다. |
| `created_at` | `createdAt` | 등록일시 | `timestamptz` | O | `now()` | - | 사진 등록 시각 |
| `updated_at` | `updatedAt` | 수정일시 | `timestamptz` | O | `now()` | - | 사진 정보 마지막 수정 시각 |
| `deleted_at` | `deletedAt` | 삭제일시 | `timestamptz` | X | `null` | - | 사진 삭제 시각. null이면 활성 사진 |

인덱스·추가 규칙:

- `idx_photo__folder_active_id(shared_folder_id, deleted_at, id)`
- `idx_photo__uploaded_by_app_user_id_deleted_at(uploaded_by_app_user_id, deleted_at)`
- `idx_photo__deleted_at(deleted_at)`
- `idx_photo__active_display_at(shared_folder_id, coalesce(taken_at, created_at), id) where deleted_at is null`
- 표시·정렬 시각은 `coalesce(taken_at, created_at)`이며 동일 시각의 안정적인 순서를 위해 `id`를 함께 사용한다.
- 날짜 그룹은 클라이언트 표시 시간대에서 계산한다. 서버가 그룹을 계산하면 API가 IANA timezone을 받아야 한다.
- 사진의 유효 삭제 기준 시각은 PostgreSQL `least(photo.deleted_at, shared_folder.deleted_at)`로 계산한 가장 이른 non-null 시각이다.
- 유효 삭제 기준 시각으로부터 30일 뒤 Object Storage 객체 삭제에 성공한 경우에만 사진 행을 물리 삭제한다. 객체 삭제 실패 시 행을 남겨 재시도한다.
- 로컬 저장공간 또는 로컬 앨범에서 공유 폴더로 사진을 불러오는 플로우는 사진 업로드로 처리한다.
- 공유 사진 또는 공유 앨범을 로컬 저장공간으로 복사하는 플로우는 다운로드이며 서버 행을 생성하지 않는다.

### 5.10 `photo_like` — 사진 좋아요

사용자가 특정 사진에 누른 단순 좋아요의 현재 상태를 저장한다. 좋아요 이력이나 반응 종류는 저장하지 않는다.

| 컬럼 | 애플리케이션 필드 | 한글명 | 타입 | 필수 | 기본값 | 키·제약 | 정의 |
|---|---|---|---|---|---|---|---|
| `id` | `id` | 좋아요 식별자 | `uuid` | O | - | PK | 사진 좋아요 행 식별자 |
| `photo_id` | `photoId` | 사진 식별자 | `uuid` | O | - | FK → `photo.id` | 좋아요 대상 사진 |
| `app_user_id` | `appUserId` | 사용자 식별자 | `uuid` | O | - | FK → `app_user.id` | 좋아요를 누른 사용자 |
| `created_at` | `createdAt` | 생성일시 | `timestamptz` | O | `now()` | - | 좋아요 생성 시각 |
| `updated_at` | `updatedAt` | 수정일시 | `timestamptz` | O | `now()` | - | 좋아요 행 마지막 수정 시각 |

인덱스·추가 규칙:

- `uk_photo_like__photo_id_app_user_id(photo_id, app_user_id)`
- `idx_photo_like__app_user_id_created_at(app_user_id, created_at)`
- 좋아요 취소는 활성 멤버이면서 `photo_like.app_user_id`가 요청 사용자와 일치할 때만 허용한다. 취소 시 행을 물리 삭제하며 이후 다시 좋아요를 생성할 수 있다.

### 5.11 `photo_comment` — 사진 댓글

단일 사진 상세 화면에 작성하는 댓글을 저장한다. 공유 폴더 채팅 뷰는 이 댓글을 공유 폴더 범위로 모아 보여준다.

| 컬럼 | 애플리케이션 필드 | 한글명 | 타입 | 필수 | 기본값 | 키·제약 | 정의 |
|---|---|---|---|---|---|---|---|
| `id` | `id` | 댓글 식별자 | `uuid` | O | - | PK | 사진 댓글 식별자 |
| `photo_id` | `photoId` | 사진 식별자 | `uuid` | O | - | FK → `photo.id` | 댓글 대상 사진 |
| `app_user_id` | `appUserId` | 작성 사용자 식별자 | `uuid` | O | - | FK → `app_user.id` | 댓글 작성 사용자 |
| `content` | `content` | 댓글 내용 | `varchar(1000)` | O | - | CK: 공백 불가 | 최대 1,000자의 댓글 본문 |
| `created_at` | `createdAt` | 작성일시 | `timestamptz` | O | `now()` | - | 댓글 작성 시각 |
| `updated_at` | `updatedAt` | 수정일시 | `timestamptz` | O | `now()` | - | 댓글 마지막 수정 시각 |
| `deleted_at` | `deletedAt` | 삭제일시 | `timestamptz` | X | `null` | - | 댓글 삭제 시각. null이면 활성 댓글 |

인덱스·추가 규칙:

- `idx_photo_comment__photo_id_deleted_at_created_at(photo_id, deleted_at, created_at)`
- `idx_photo_comment__app_user_id_deleted_at(app_user_id, deleted_at)`
- `idx_photo_comment__active_created_id(deleted_at, created_at, id)`
- 활성 공유 폴더 멤버만 작성할 수 있고 작성자만 수정·삭제할 수 있다.

## 6. Check 제약 사전

| 제약 이름 | 테이블 | 조건 | 목적 |
|---|---|---|---|
| `chk_app_user__display_name_not_blank` | `app_user` | `char_length(btrim(display_name)) > 0` | 공백 사용자명 차단 |
| `chk_refresh_token__expires_after_created` | `refresh_token` | `expires_at > created_at` | 생성 시각 이전 만료 차단 |
| `chk_refresh_token__revoked_after_created` | `refresh_token` | `revoked_at is null or revoked_at >= created_at` | 생성 시각 이전 폐기 차단 |
| `chk_invite_code_reservation__invite_code_not_blank` | `invite_code_reservation` | `char_length(btrim(invite_code)) > 0` | 공백 예약 코드 차단 |
| `chk_shared_folder__name_not_blank` | `shared_folder` | `char_length(btrim(name)) > 0` | 공백 공유 폴더 이름 차단 |
| `chk_shared_folder__invite_code_not_blank` | `shared_folder` | `char_length(btrim(invite_code)) > 0` | 공백 초대 코드 차단 |
| `chk_device__name_not_blank` | `device` | `char_length(btrim(name)) > 0` | 공백 기기명 차단 |
| `chk_shared_album__name_not_blank` | `shared_album` | `char_length(btrim(name)) > 0` | 공백 앨범 이름 차단 |
| `chk_photo__file_size_positive` | `photo` | `file_size > 0` | 0 byte 이하 파일 차단 |
| `chk_photo__content_type_image` | `photo` | `content_type like 'image/%'` | 이미지 이외 MIME type 차단 |
| `chk_photo__original_file_name_not_blank` | `photo` | `char_length(btrim(original_file_name)) > 0` | 공백 원본 파일명 차단 |
| `chk_photo_comment__content_not_blank` | `photo_comment` | `char_length(btrim(content)) > 0` | 공백 댓글 차단 |

## 7. 외래 키와 물리 삭제 동작

| FK | 자식 → 부모 | `ON DELETE` | 판단 이유 |
|---|---|---|---|
| `fk_refresh_token__app_user` | `refresh_token.app_user_id` → `app_user.id` | cascade | 사용자 물리 삭제 시 토큰도 제거 |
| `fk_refresh_token__replaced_by` | `refresh_token.replaced_by_refresh_token_id` → `refresh_token.id` | set null | 다음 토큰 행 제거 시 이전 행의 선택 참조 해제 |
| `fk_shared_folder__invite_code_reservation` | `shared_folder.invite_code` → `invite_code_reservation.invite_code` | restrict | 사용 중인 예약 코드 삭제 차단 |
| `fk_shared_folder__created_by_app_user` | `shared_folder.created_by_app_user_id` → `app_user.id` | restrict | 공유 폴더 최초 생성자 이력 보존 |
| `fk_shared_folder_membership__shared_folder` | `shared_folder_membership.shared_folder_id` → `shared_folder.id` | cascade | 공유 폴더 물리 정리 시 멤버십 제거 |
| `fk_shared_folder_membership__app_user` | `shared_folder_membership.app_user_id` → `app_user.id` | restrict | 남아 있는 멤버십보다 사용자가 먼저 물리 삭제되는 것 방지 |
| `fk_device__app_user` | `device.app_user_id` → `app_user.id` | cascade | 사용자 물리 삭제 시 기기 태그 제거 |
| `fk_shared_album__shared_folder` | `shared_album.shared_folder_id` → `shared_folder.id` | cascade | 공유 폴더 물리 정리 시 앨범 제거 |
| `fk_shared_album__created_by_app_user` | `shared_album.created_by_app_user_id` → `app_user.id` | restrict | 앨범 생성자 이력 보존 |
| `fk_shared_album_photo__shared_album` | `shared_album_photo.shared_album_id` → `shared_album.id` | cascade | 앨범 물리 삭제 시 포함 관계 제거 |
| `fk_shared_album_photo__photo` | `shared_album_photo.photo_id` → `photo.id` | cascade | 사진 물리 삭제 시 포함 관계 제거 |
| `fk_shared_album_photo__added_by_app_user` | `shared_album_photo.added_by_app_user_id` → `app_user.id` | restrict | 앨범 사진 추가자 이력 보존 |
| `fk_photo__shared_folder` | `photo.shared_folder_id` → `shared_folder.id` | cascade | 공유 폴더 물리 정리 시 사진 행 제거 |
| `fk_photo__uploaded_by_app_user` | `photo.uploaded_by_app_user_id` → `app_user.id` | restrict | 사진 업로더 이력 보존 |
| `fk_photo_like__photo` | `photo_like.photo_id` → `photo.id` | cascade | 사진 물리 삭제 시 좋아요 제거 |
| `fk_photo_like__app_user` | `photo_like.app_user_id` → `app_user.id` | cascade | 사용자 물리 삭제 시 좋아요 제거 |
| `fk_photo_comment__photo` | `photo_comment.photo_id` → `photo.id` | cascade | 사진 물리 삭제 시 댓글 제거 |
| `fk_photo_comment__app_user` | `photo_comment.app_user_id` → `app_user.id` | restrict | 댓글 작성자 이력 보존 |

Soft delete에는 `ON DELETE`가 동작하지 않는다. 모든 조회와 쓰기는 대상 행뿐 아니라 상위 공유 폴더의 활성 상태를 함께 확인한다.

## 8. 서비스 계층 무결성·권한 사전

DB 외래 키만으로 표현하지 않는 다음 규칙은 서비스 트랜잭션과 통합 테스트로 강제한다.

| 대상 작업 | 필수 검증 |
|---|---|
| 공유 폴더 생성 | 코드 영구 예약, 공유 폴더 생성, 생성자의 `HOST` 멤버십 생성을 단일 트랜잭션으로 처리 |
| 공유 폴더 참여 | 활성 공유 폴더와 초대 코드 일치, 기존 활성 멤버십 부재 확인 |
| 공유 폴더 이름 수정 | 요청 사용자의 해당 공유 폴더 활성 `HOST` 역할 확인 |
| 공유 폴더 삭제 | 요청 사용자의 해당 공유 폴더 활성 `HOST` 역할 확인 |
| 앨범 생성 | 요청 사용자의 해당 공유 폴더 활성 멤버십 확인 |
| 앨범 정보 수정 | 요청 사용자의 해당 공유 폴더 활성 멤버십 확인 |
| 앨범 삭제 | 활성 멤버십과 `created_by_app_user_id` 일치 확인 |
| 사진 업로드 | 대상 공유 폴더와 최초 포함 대상 앨범의 활성 상태, 요청 사용자의 활성 멤버십 확인 |
| 사진을 앨범에 추가 | 대상 앨범과 사진이 같은 활성 공유 폴더에 속하는지, 요청 사용자의 활성 멤버십이 있는지 확인 |
| 사진을 앨범에서만 제거 | 대상 포함 관계의 앨범과 사진이 같은 활성 공유 폴더에 속하는지, 요청 사용자의 활성 멤버십이 있는지 확인 |
| 사진 원본 수정·삭제 | 활성 멤버십과 `uploaded_by_app_user_id` 일치 확인 |
| 사진 좋아요 생성·댓글 작성 | 대상 사진의 활성 공유 폴더와 요청 사용자의 활성 멤버십 확인 |
| 사진 좋아요 취소 | 활성 멤버십과 `photo_like.app_user_id` 일치 확인 |
| 댓글 수정·삭제 | 활성 멤버십과 `app_user_id` 일치 확인 |
| 채팅 뷰 조회 | 요청 사용자의 해당 공유 폴더 활성 멤버십 확인, 활성 사진·댓글만 반환 |

필수 통합 테스트에는 다른 공유 폴더 사용자의 교차 범위 쓰기 차단, 나간 멤버의 쓰기 차단, 타인 콘텐츠 수정·삭제 차단을 포함한다.

## 9. 물리 정리 순서

물리 정리는 Object Storage 객체 삭제와 DB 삭제를 분리하고, DB 종속 행은 정의된 FK `ON DELETE CASCADE`로 정리한다.

1. 개별 사진은 30일이 지나면 OCI Object Storage 객체를 먼저 삭제한다.
2. 객체 삭제가 성공했거나 객체가 이미 없으면 `photo` 행을 삭제하고 사진 좋아요, 댓글, 앨범 사진 포함 관계를 FK cascade로 함께 삭제한다.
3. 개별 앨범은 30일이 지나면 `shared_album` 행을 삭제하고 앨범 사진 포함 관계를 FK cascade로 함께 삭제한다. 사진 원본과 Object Storage 객체는 유지한다.
4. 공유 폴더는 30일이 지난 뒤 폴더에 속한 모든 사진의 Object Storage 객체 삭제가 성공했거나 객체가 이미 없는지 확인한다.
5. 확인이 끝나면 `shared_folder` 행을 삭제하고 멤버십, 앨범, 사진과 그 종속 행을 FK cascade로 함께 삭제한다.
6. Object Storage 객체 삭제에 실패하면 관련 DB 행을 유지해 다음 배치에서 재시도한다.
7. `invite_code_reservation`은 삭제하지 않는다.

## 10. 5단계 완료 기준

- 11개 테이블과 전체 컬럼의 한글명, 타입, 필수 여부, 기본값, 키와 정의를 작성한다.
- 1개 Enum의 허용 코드값과 의미를 작성한다.
- 18개 외래 키의 물리 삭제 동작을 작성한다.
- DBML에 정의한 일반 인덱스와 PostgreSQL 보완 SQL의 partial/expression index를 작성한다.
- DB 외래 키로 강제하지 않는 공유 폴더 범위와 콘텐츠 소유권 규칙을 작성한다.
- soft delete, 30일 물리 정리와 초대 코드 영구 보존 정책을 작성한다.

위 항목을 충족하면 5단계 데이터 사전 작성을 완료한 것으로 본다.
