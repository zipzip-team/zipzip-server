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
| `device` | 기기 | 사용자의 주 사용 촬영 기기 표시용 태그 |
| `shared_group_chat_message` | 그룹 채팅 메시지 | 공유 그룹 안에서 작성하는 일반 채팅 메시지 |
| `shared_album` | 공유집(앨범) | 공유 그룹 안에서 사진을 담는 단위 |
| `photo` | 사진 | 공유집(앨범)에 업로드된 사진 원본의 Object Storage 참조 |
| `photo_like` | 사진 좋아요 | 사용자가 사진에 좋아요를 누른 상태 |
| `photo_comment` | 사진 댓글 | 단일 사진에 달리는 댓글 |

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

### 3.6 `device`

사용자의 주 사용 촬영 기기를 표시하기 위한 태그이다.

| 컬럼 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | `uuid` | O | 기기 태그 식별자 |
| `app_user_id` | `uuid` | O | 소유 사용자 |
| `name` | `varchar(100)` | O | 표시용 기기명 |
| `created_at` | `timestamptz` | O | 생성 시각 |
| `updated_at` | `timestamptz` | O | 수정 시각 |
| `deleted_at` | `timestamptz` | X | 삭제 시각 |

주요 제약:

- `name` 공백 불가
- PostgreSQL 보완 SQL에서 사용자별 활성 기기명 중복 방지
- 사용자 탈퇴 시 활성 기기는 soft delete

### 3.7 `shared_group_chat_message`

공유 그룹 안에서 사진 컨텍스트 없이 작성하는 일반 채팅 메시지이다.
1차 구현은 폴링으로 새 메시지를 조회한다.

| 컬럼 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | `uuid` | O | 메시지 식별자 |
| `shared_group_id` | `uuid` | O | 메시지가 속한 공유 그룹 |
| `app_user_id` | `uuid` | O | 작성 사용자 |
| `content` | `varchar(1000)` | O | 메시지 본문 |
| `created_at` | `timestamptz` | O | 작성 시각 |
| `updated_at` | `timestamptz` | O | 수정 시각 |
| `deleted_at` | `timestamptz` | X | 삭제 시각 |

주요 제약:

- `content` 공백 불가
- 메시지 작성과 조회는 활성 공유 그룹 멤버십 필요
- 메시지 수정과 삭제는 작성자만 가능
- 폴링 조회는 `created_at`, `id` 커서 기준

### 3.8 `shared_album`

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

### 3.9 `photo`

공유집(앨범)에 업로드된 사진 원본의 파일 참조 정보를 저장한다.

| 컬럼 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | `uuid` | O | 사진 식별자 |
| `shared_album_id` | `uuid` | O | 사진이 속한 공유집(앨범) |
| `uploaded_by_app_user_id` | `uuid` | O | 업로드 사용자 |
| `object_key` | `text` | O | OCI Object Storage 객체 키 |
| `original_file_name` | `varchar(255)` | O | 업로드 당시 원본 파일명 |
| `content_type` | `varchar(100)` | O | 이미지 MIME type |
| `file_size` | `bigint` | O | 파일 크기 byte |
| `taken_at` | `timestamptz` | X | EXIF에서 추출한 촬영일시 |
| `created_at` | `timestamptz` | O | 등록 시각 |
| `updated_at` | `timestamptz` | O | 수정 시각 |
| `deleted_at` | `timestamptz` | X | 삭제 시각 |

주요 제약:

- `object_key` unique
- `file_size > 0`
- `content_type like 'image/%'`
- `original_file_name` 공백 불가
- 원본 수정과 삭제는 원칙적으로 업로더만 가능
- 업로더가 탈퇴한 경우 삭제는 공유 그룹 방장이 가능

### 3.10 `photo_like`

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

### 3.11 `photo_comment`

단일 사진에 달리는 댓글이다.

| 컬럼 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | `uuid` | O | 댓글 식별자 |
| `photo_id` | `uuid` | O | 댓글 대상 사진 |
| `app_user_id` | `uuid` | O | 작성 사용자 |
| `content` | `varchar(1000)` | O | 댓글 본문 |
| `created_at` | `timestamptz` | O | 작성 시각 |
| `updated_at` | `timestamptz` | O | 수정 시각 |
| `deleted_at` | `timestamptz` | X | 삭제 시각 |

주요 제약:

- `content` 공백 불가
- 댓글 작성과 조회는 활성 공유 그룹 멤버십 필요
- 댓글 수정과 삭제는 작성자만 가능

## 4. 외래 키

| 제약명 | 관계 | 삭제 규칙 |
|---|---|---|
| `fk_refresh_token__app_user` | `refresh_token.app_user_id` -> `app_user.id` | cascade |
| `fk_refresh_token__replaced_by` | `refresh_token.replaced_by_refresh_token_id` -> `refresh_token.id` | set null |
| `fk_shared_group__invite_code_reservation` | `shared_group.invite_code` -> `invite_code_reservation.invite_code` | restrict |
| `fk_shared_group__created_by_app_user` | `shared_group.created_by_app_user_id` -> `app_user.id` | restrict |
| `fk_shared_group_membership__shared_group` | `shared_group_membership.shared_group_id` -> `shared_group.id` | cascade |
| `fk_shared_group_membership__app_user` | `shared_group_membership.app_user_id` -> `app_user.id` | restrict |
| `fk_device__app_user` | `device.app_user_id` -> `app_user.id` | cascade |
| `fk_shared_group_chat_message__shared_group` | `shared_group_chat_message.shared_group_id` -> `shared_group.id` | cascade |
| `fk_shared_group_chat_message__app_user` | `shared_group_chat_message.app_user_id` -> `app_user.id` | restrict |
| `fk_shared_album__shared_group` | `shared_album.shared_group_id` -> `shared_group.id` | cascade |
| `fk_shared_album__created_by_app_user` | `shared_album.created_by_app_user_id` -> `app_user.id` | restrict |
| `fk_photo__shared_album` | `photo.shared_album_id` -> `shared_album.id` | cascade |
| `fk_photo__uploaded_by_app_user` | `photo.uploaded_by_app_user_id` -> `app_user.id` | restrict |
| `fk_photo_like__photo` | `photo_like.photo_id` -> `photo.id` | cascade |
| `fk_photo_like__app_user` | `photo_like.app_user_id` -> `app_user.id` | cascade |
| `fk_photo_comment__photo` | `photo_comment.photo_id` -> `photo.id` | cascade |
| `fk_photo_comment__app_user` | `photo_comment.app_user_id` -> `app_user.id` | restrict |

## 5. 주요 인덱스

| 테이블 | 인덱스 | 목적 |
|---|---|---|
| `shared_group_membership` | `uk_shared_group_membership__group_user` | 사용자 중복 참여 방지 |
| `shared_group_membership` | `uk_shared_group_membership__host` | 공유 그룹별 방장 1명 보장 |
| `shared_group_chat_message` | `idx_shared_group_chat_message__group_active_created_id` | 폴링 메시지 조회 |
| `shared_album` | `idx_shared_album__shared_group_id_deleted_at_created_at` | 공유 그룹별 공유집(앨범) 목록 |
| `photo` | `idx_photo__album_active_id` | 공유집(앨범)별 활성 사진 조회 |
| `photo` | `idx_photo__active_display_at` | 촬영일 우선 사진 정렬 |
| `photo_like` | `uk_photo_like__photo_id_app_user_id` | 중복 좋아요 방지 |
| `photo_comment` | `idx_photo_comment__photo_id_deleted_at_created_at` | 사진별 댓글 목록 |
| `device` | `uk_device__active_name` | 사용자별 활성 기기명 중복 방지 |

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
| 사진 업로드 | 대상 공유집(앨범)의 활성 상태와 요청 사용자의 상위 공유 그룹 활성 멤버십 확인 |
| 사진 공유집(앨범) 이동 | 기능 채택 시 원본·대상 공유집(앨범)이 같은 활성 공유 그룹에 속하는지, 요청 사용자의 활성 멤버십이 있는지 확인 |
| 사진 원본 수정 | 활성 멤버십과 `photo.uploaded_by_app_user_id` 일치 확인 |
| 사진 원본 삭제 | 업로더는 활성 멤버십과 `photo.uploaded_by_app_user_id` 일치 확인. 업로더가 탈퇴한 경우 공유 그룹 `HOST` 멤버십 확인 |
| 사진 좋아요 생성·취소 | 요청 사용자의 활성 멤버십 확인. 취소는 본인 좋아요만 허용 |
| 사진 댓글 작성 | 요청 사용자의 활성 멤버십 확인 |
| 사진 댓글 수정·삭제 | 활성 멤버십과 `photo_comment.app_user_id` 일치 확인 |
| 그룹 채팅 메시지 조회 | 요청 사용자의 해당 공유 그룹 활성 멤버십 확인 |
| 그룹 채팅 메시지 작성 | 요청 사용자의 해당 공유 그룹 활성 멤버십 확인 |
| 그룹 채팅 메시지 수정·삭제 | 활성 멤버십과 `shared_group_chat_message.app_user_id` 일치 확인 |

## 7. 삭제 정리 순서

1. 공유 그룹 삭제, 공유집(앨범) 삭제, 사진 삭제, 댓글 삭제, 그룹 채팅 메시지 삭제는 각 테이블의 `deleted_at` 기록으로 처리한다.
2. 사진 좋아요 취소는 관계 행을 즉시 물리 삭제한다.
3. 사용자 탈퇴 시 활성 Refresh Token을 폐기하고 `app_user.deleted_at`을 기록한다.
4. 사용자 탈퇴 시 `app_user.display_name`을 "탈퇴한 사용자"로 갱신하고 `app_user` 행은 물리 삭제하지 않는다.
5. 사용자 탈퇴 시 방장으로 만든 공유 그룹은 soft delete하고, `MEMBER`로 참여 중인 공유 그룹 멤버십은 물리 삭제한다.
6. 사용자 탈퇴 시 활성 기기는 soft delete하고 사진 좋아요는 물리 삭제한다.
7. 탈퇴한 사용자가 기존에 생성·작성·업로드한 공유 콘텐츠는 즉시 삭제하지 않고 사용자 표시는 "탈퇴한 사용자"로 대체한다.
8. 공유 그룹 삭제 후 30일이 지나면 하위 사진의 Object Storage 객체를 먼저 삭제하거나 삭제 재시도 작업을 기록한다.
9. 객체 삭제가 성공했거나 삭제 재시도 작업을 기록했거나 객체가 이미 없으면 `shared_group` 행을 삭제하고 멤버십, 채팅 메시지, 공유집(앨범), 사진과 종속 행을 FK cascade로 함께 삭제한다.
10. 개별 삭제한 공유집(앨범)은 30일 뒤 하위 사진 객체 삭제가 끝난 뒤 `shared_album` 행을 삭제한다.
11. 개별 삭제한 사진은 30일 뒤 Object Storage 객체 삭제가 끝난 뒤 `photo` 행을 삭제한다.
12. 공유 그룹 물리 삭제 트랜잭션에서 해당 초대 코드 예약 원장 행도 삭제한다.
