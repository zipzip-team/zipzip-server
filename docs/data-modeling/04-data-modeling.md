# Zipzip 데이터 모델링

## 1. 모델링 원칙

- 주요 테이블의 PK는 애플리케이션에서 생성한 `uuid`를 사용한다.
- 테이블명과 DB 컬럼명은 `snake_case`를 사용한다.
- Java/Kotlin 필드와 API JSON 필드는 `camelCase`를 사용한다.
- 삭제 정책은 데이터 성격에 따라 soft delete, 상태 변경, 물리 삭제를 구분한다.
- 모든 주요 업무 테이블은 `created_at`, `updated_at`을 가진다. 예약 테이블은 예외를 둔다.
- soft delete 대상 테이블은 `deleted_at`을 가진다.
- 하단 네비게이션의 공유 탭은 DB 엔티티로 만들지 않는다.
- 메인, 사진, 사진집은 로컬 중심 기능이다.
- 공유 탭의 서버 위계는 공유 그룹, 공유집(앨범), 사진 순서이다.
- 기존 `shared_house` 물리 테이블은 두지 않고 공유집(앨범)을 `shared_album`으로 저장한다.
- 사진은 반드시 하나의 공유집(앨범)에 직접 속한다.
- 공유 플로우에서만 서버에 사진을 업로드한다.
- 이미지 파일은 OCI Object Storage에 저장한다.
- DB에는 Object Storage 참조에 필요한 최소 파일 정보만 저장한다.
- 공유 그룹 관리 화면에서 방장/멤버별 기기 태그를 표시하기 위해 사용자 단위 기기명을 저장한다.
- 그룹 채팅 메시지는 `shared_group_chat_message`에 저장하고 1차 구현은 폴링으로 조회한다.
- 날짜별 사진 그룹은 촬영일 기준으로 표시하고 촬영일이 없으면 생성 시각을 사용한다.
- 공유집(앨범) 사진 수는 활성 사진 기준으로 실시간 count한다.
- 공유집(앨범) 정보 수정은 활성 공유 그룹 멤버가 할 수 있고, 삭제는 생성자 또는 탈퇴한 생성자의 공유 그룹 방장이 할 수 있다.
- 사진 원본은 원칙적으로 업로더만 수정·삭제할 수 있고, 업로더가 탈퇴한 경우 삭제는 공유 그룹 방장이 할 수 있다.
- 사진 댓글과 그룹 채팅 메시지는 작성자만 수정·삭제할 수 있다.
- 멤버 강제 퇴장은 제공하지 않는다.
- 나간 멤버는 공유 그룹 초대 코드로 다시 참여할 수 있다.
- 공유 그룹은 방장만 삭제할 수 있고 사용자에게 복구 기능을 제공하지 않는다.
- 공유 그룹 이름 같은 그룹 정보 수정은 방장만 할 수 있다.
- 개별 삭제한 공유집(앨범)·사진과 삭제된 공유 그룹의 데이터는 30일 뒤 물리 삭제한다.
- 초대 코드 예약 원장은 공유 그룹이 존재하는 동안만 코드 점유를 보장하고, 공유 그룹 물리 삭제 시 함께 삭제해 코드를 다시 사용할 수 있게 한다.
- Zipzip 서비스 탈퇴 시 `app_user.deleted_at`을 기록하고 활성 Refresh Token을 폐기한다.
- Zipzip 서비스 탈퇴 시 `app_user.display_name`은 "탈퇴한 사용자"로 갱신하고 `app_user` 행은 재가입 복구를 위해 물리 삭제하지 않는다.
- Zipzip 서비스 탈퇴 시 사용자의 활성 기기는 soft delete하고, 사용자의 사진 좋아요는 물리 삭제한다.
- 탈퇴한 사용자가 기존에 생성·작성·업로드한 공유 콘텐츠는 즉시 삭제하지 않고, 사용자 표시는 "탈퇴한 사용자"로 대체한다.
- 탈퇴한 `MEMBER`가 만든 공유집(앨범)과 업로드한 사진의 삭제는 해당 공유 그룹의 `HOST`가 할 수 있다.
- Zipzip 서비스 탈퇴 시 방장으로 만든 공유 그룹은 soft delete하고 `MEMBER`로 참여 중인 공유 그룹 멤버십은 물리 삭제한다. 삭제된 공유 그룹의 `HOST` 멤버십은 공유 그룹 물리 삭제 시 FK cascade로 함께 삭제한다.
- 동일한 Apple 계정으로 재가입하면 기존 `app_user`를 복구하되 과거 공유 그룹 멤버십은 자동 복구하지 않는다.

## 2. 최종 도메인 계층

```text
공유 탭(UI, 테이블 없음)
└── 공유 그룹(shared_group)
    ├── 공유 그룹 멤버십(shared_group_membership)
    ├── 그룹 채팅 메시지(shared_group_chat_message)
    └── 공유집(앨범, shared_album)
        └── 사진(photo)
            ├── 사진 좋아요(photo_like)
            └── 사진 댓글(photo_comment)
```

공유 그룹 카드 하나는 `shared_group` 한 행에 해당한다.
공유집(앨범) 카드 하나는 `shared_album` 한 행에 해당한다.
사진 원본은 `photo` 한 행에 해당하며 `photo.shared_album_id`로 직접 소속 공유집(앨범)을 가진다.

## 3. 테이블 후보 선정 기준

테이블 후보를 판단할 때 아래 질문을 사용한다.

- 로그인 이후 서버에서 지속적으로 보존해야 하는 데이터인가?
- 공유 그룹, 공유집(앨범), 사진의 권한 검증에 필요한가?
- Object Storage 파일 참조나 삭제 정리에 필요한가?
- 다른 사용자가 함께 조회하거나 수정하는 데이터인가?
- 중복 생성, 재사용, 삭제 정책을 DB 제약으로 보강해야 하는가?

로그인 전 로컬 기능, 사진집 탭의 로컬 데이터, 단순 화면 이름, 디자인 컴포넌트는 테이블 후보에서 제외한다.

## 4. 1차 ERD 테이블

| 한글 | 물리 테이블 | 필요 여부 | 이유 |
|---|---|---|---|
| 사용자 | `app_user` | 필요 | Apple 로그인 이후 서버 기준 사용자와 작성자·생성자·업로더 주체를 저장한다. |
| Refresh Token | `refresh_token` | 필요 | 자체 JWT Refresh Token 회전, 폐기, 세션 관리를 지원한다. |
| 초대 코드 예약 원장 | `invite_code_reservation` | 필요 | 활성 또는 soft delete 상태의 공유 그룹 초대 코드 점유를 보장하고, 공유 그룹 물리 삭제 시 점유를 해제한다. |
| 공유 그룹 | `shared_group` | 필요 | 초대 코드로 참여하는 최상위 공유 공간을 저장한다. |
| 공유 그룹 멤버십 | `shared_group_membership` | 필요 | 사용자가 어떤 공유 그룹에 어떤 역할로 참여하는지 저장한다. |
| 기기 | `device` | 필요 | 사용자 단위 기기 태그를 저장하고 공유 그룹 멤버별 표시에 사용한다. |
| 그룹 채팅 메시지 | `shared_group_chat_message` | 필요 | 공유 그룹 안의 일반 채팅 메시지를 저장한다. |
| 공유집(앨범) | `shared_album` | 필요 | 공유 그룹 안에서 사진을 담는 하위 단위를 저장한다. |
| 사진 | `photo` | 필요 | 공유집(앨범)에 업로드된 사진 원본의 Object Storage 참조 정보를 저장한다. |
| 사진 좋아요 | `photo_like` | 필요 | 사용자별 사진 좋아요 상태를 저장한다. |
| 사진 댓글 | `photo_comment` | 필요 | 단일 사진에 달리는 댓글을 저장한다. |

1차 모델은 11개 테이블이다.
별도 `shared_house`, `shared_album_photo`, `chat_room`, `chat_message` 테이블은 두지 않는다.

## 5. 테이블별 설명

### 5.1 `app_user`

Apple 로그인 후 서버에 등록된 사용자 정보를 저장한다.
탈퇴 시 `app_user.deleted_at`을 기록하고, 공유 콘텐츠에서 사용자 표시는 "탈퇴한 사용자"로 대체한다.
탈퇴 시 `display_name`은 "탈퇴한 사용자"로 갱신하고, `app_user` 행은 재가입 복구를 위해 물리 삭제하지 않는다.
동일한 Apple 계정으로 재가입하면 기존 `app_user` 행의 `deleted_at`을 해제한다.

### 5.2 `refresh_token`

서버가 발급한 Refresh Token의 해시와 회전 상태를 저장한다.
Access Token과 Refresh Token 원문은 DB에 저장하지 않는다.

### 5.3 `invite_code_reservation`

공유 그룹 생성 시 발급한 초대 코드를 예약하는 원장을 저장한다.
이 테이블은 예약 행의 상태 변경을 기록하지 않으므로 `invite_code` 자체를 PK로 사용하고 `updated_at`을 두지 않는다.

### 5.4 `shared_group`

공유 탭에 카드로 표시되는 최상위 공유 공간을 저장한다.
초대 코드는 `invite_code_reservation`에서 예약하고 `shared_group.invite_code`가 이를 참조한다.
공유 그룹 이름 같은 그룹 정보는 방장만 수정할 수 있다.
공유 그룹은 방장만 삭제할 수 있고 사용자에게 복구 기능을 제공하지 않는다.

### 5.5 `shared_group_membership`

사용자와 공유 그룹 사이의 참여 관계를 저장한다.

역할:

- `HOST`: 공유 그룹을 만든 방장
- `MEMBER`: 초대 코드로 참여한 멤버

멤버 강제 퇴장은 제공하지 않는다.
멤버가 직접 나가면 `MEMBER` 멤버십 행을 물리 삭제한다.
멤버십 행이 존재하고 상위 공유 그룹과 사용자가 soft delete되지 않은 경우에만 활성 멤버십으로 판단한다.

### 5.6 `device`

사용자가 주로 사용하는 촬영 기기의 표시용 이름을 저장한다.
기기는 물리 장비 식별자가 아니며 별도 유형을 두지 않는다.
기기는 공유 그룹 멤버십이나 사진에 직접 종속되지 않는다.

### 5.7 `shared_group_chat_message`

공유 그룹 안에서 작성한 일반 채팅 메시지를 저장한다.
메시지 작성과 조회는 공유 그룹 활성 멤버십을 요구한다.
메시지 수정과 삭제는 작성자만 할 수 있다.
1차 구현은 `created_at`, `id` 커서를 사용하는 폴링 조회를 기준으로 한다.

### 5.8 `shared_album`

공유 그룹 안에서 사진을 담는 공유집(앨범)을 저장한다.

필요한 이유:

- 공유 그룹 아래의 공유집(앨범) 목록 표현
- 공유집(앨범) 이름과 생성자 저장
- 사진이 없는 공유집(앨범) 표현
- 사진 업로드와 사진 목록의 조회 범위 제공
- 공유집(앨범) 삭제 상태 관리

공유집(앨범)은 활성 공유 그룹 멤버가 생성할 수 있다.
공유집(앨범) 이름 같은 정보 수정은 활성 공유 그룹 멤버가 할 수 있다.
공유집(앨범) 삭제는 해당 공유집(앨범) 생성자가 할 수 있고, 생성자가 탈퇴한 사용자인 경우 상위 공유 그룹의 방장이 할 수 있다.

### 5.9 `photo`

공유집(앨범) 안에 업로드된 사진 원본의 Object Storage 참조 정보를 저장한다.

필요한 이유:

- 사진이 속한 공유집(앨범)과 업로더 연결
- 원본 이미지 Object Storage key 저장
- 표시와 정렬에 사용할 촬영일시 저장
- 사진 원본 수정·삭제 권한 기준 제공
- 사진 원본 삭제 상태 관리

사진은 업로드 시 반드시 하나의 공유집(앨범)에 속한다.
사진 원본 수정·삭제는 원칙적으로 업로더가 할 수 있고, 업로더가 탈퇴한 사용자인 경우 삭제는 상위 공유 그룹의 방장이 할 수 있다.
사진을 다른 공유집(앨범)으로 옮기는 기능이 필요하면 `photo.shared_album_id`를 변경하는 이동으로 처리한다.
같은 이미지를 여러 공유집(앨범)에 별도로 두려면 별도 사진 업로드 또는 복제 정책을 정의해야 한다.
로컬 저장공간 또는 로컬 사진집에서 공유집(앨범)으로 사진을 불러오는 플로우는 사진 업로드로 처리한다.
공유 사진 또는 공유 앨범을 로컬 저장공간으로 복사하는 플로우는 다운로드이며 서버 엔티티를 생성하지 않는다.

### 5.10 `photo_like`

사용자가 특정 사진에 좋아요를 누른 상태를 저장한다.
좋아요 취소 시 `photo_like` 행을 물리 삭제한다.
사용자 탈퇴 시 해당 사용자의 `photo_like` 행도 물리 삭제해 활성 좋아요 수에 탈퇴 사용자를 포함하지 않는다.

### 5.11 `photo_comment`

단일 사진에 달리는 댓글을 저장한다.
댓글 작성, 수정, 삭제는 사진이 속한 앨범의 상위 공유 그룹 활성 멤버십을 요구한다.
댓글 수정과 삭제는 작성자만 할 수 있다.

## 6. 제외 테이블

| 후보 | 제외 이유 |
|---|---|
| `shared_house` | 공유집을 별도 물리 테이블로 두지 않고 `shared_album`으로 저장한다. |
| `shared_album_photo` | 사진은 반드시 하나의 공유집(앨범)에 직접 속하므로 N:M 포함 관계를 두지 않는다. |
| `photo_book` | 로컬 중심 기능이며 서버와 자동 동기화하지 않는다. |
| `photo_metadata` | 서버가 이미지 EXIF에서 촬영일시만 추출해 nullable `photo.taken_at`에 저장하고 별도 메타데이터 테이블은 두지 않는다. |
| `chat_room`, `chat_message` | 그룹 채팅은 `shared_group_chat_message`로 충분하다. |
| `shared_album_like` | 좋아요 UI는 사진 중심이며 앨범 좋아요 근거가 부족하다. |

## 7. 관계 요약

```text
app_user
├── refresh_token
├── device
├── shared_group
├── shared_group_membership
├── shared_group_chat_message
├── shared_album
├── photo
├── photo_like
└── photo_comment

shared_group
├── shared_group_membership
├── shared_group_chat_message
└── shared_album

shared_album
└── photo

photo
├── photo_like
└── photo_comment
```

- 한 공유 그룹은 여러 멤버십을 가질 수 있다.
- 한 공유 그룹은 하나의 `HOST` 멤버십만 가질 수 있다.
- 한 공유 그룹은 여러 공유집(앨범)을 가질 수 있다.
- 한 공유집(앨범)은 사진 없이 존재하거나 여러 사진을 가질 수 있다.
- 한 사진은 정확히 하나의 공유집(앨범)에 속한다.
- 한 사진은 여러 좋아요와 댓글을 가질 수 있다.
- 한 사용자는 여러 공유 그룹에 참여할 수 있다.
- 한 사용자는 여러 공유집(앨범)을 생성하고, 여러 사진을 업로드할 수 있다.
- 한 사용자는 여러 그룹 채팅 메시지와 사진 댓글을 작성할 수 있다.
- 공유집(앨범) 사진 수는 활성 사진 기준 실시간 count로 계산한다.
- 작성자·생성자·업로더는 `app_user` FK로 보존하고, 공유 그룹 내부 권한은 활성 공유 그룹 멤버십을 서비스 계층에서 검증한다.
- 탈퇴한 작성자·생성자·업로더의 화면 표시는 "탈퇴한 사용자"로 대체한다.

## 8. ERD 설계 요약

- 1차 ERD 대상은 `app_user`, `refresh_token`, `invite_code_reservation`, `shared_group`, `shared_group_membership`, `device`, `shared_group_chat_message`, `shared_album`, `photo`, `photo_like`, `photo_comment` 11개 테이블이다.
- 하단 네비게이션의 공유 탭은 테이블로 만들지 않는다.
- 사진집 탭의 로컬 데이터는 서버 모델에 포함하지 않는다.
- 초대 코드, 멤버십, 방장 역할, 관리, 그룹 채팅은 공유 그룹에 속한다.
- 공유집(앨범)은 공유 그룹 아래에서 참여자들이 생성하는 사진 묶음이다.
- 사진 원본은 공유집(앨범) 아래에 직접 속한다.
- 그룹 채팅 메시지는 `shared_group_chat_message`에 저장하며 1차 구현은 폴링 조회를 사용한다.
- 사진 댓글은 `photo_comment`에 저장하며 사진 상세 기능에 속한다.
- `photo_like`는 단순 좋아요 상태를 저장하고 취소 시 물리 삭제한다.
- 작성자·생성자·업로더는 `app_user_id` 계열 컬럼으로 저장하고 공유 그룹 내부 쓰기 권한은 활성 멤버십으로 검증한다.
- 탈퇴한 작성자·생성자·업로더는 "탈퇴한 사용자"로 표시한다.
- 공유 그룹 정보 수정과 삭제는 방장만 할 수 있다.
- 공유집(앨범) 정보 수정은 활성 멤버가 할 수 있고 삭제는 생성자 또는 탈퇴한 생성자의 공유 그룹 방장이 할 수 있다.
- 사진 원본 삭제는 업로더 또는 탈퇴한 업로더의 공유 그룹 방장이 할 수 있고 `photo.deleted_at`으로 처리한다.
- 그룹 채팅 메시지와 사진 댓글은 작성자만 수정·삭제할 수 있다.
- 멤버 강제 퇴장은 제공하지 않는다.
- 나간 멤버는 같은 공유 그룹의 유효한 초대 코드로 다시 참여할 수 있다.
- 공유 그룹은 방장만 삭제할 수 있고 사용자에게 복구 기능을 제공하지 않는다.
- 개별 삭제한 공유집(앨범)·사진과 삭제된 공유 그룹의 데이터는 30일 뒤 물리 삭제한다.
- 초대 코드는 활성 또는 soft delete 상태의 공유 그룹에서는 재사용하지 않고, 공유 그룹 물리 삭제 후에는 다시 사용할 수 있다.
- Zipzip 서비스 사용자가 탈퇴하면 `app_user.deleted_at`을 기록하고 활성 Refresh Token을 폐기한다.
- Zipzip 서비스 사용자가 탈퇴하면 `app_user.display_name`을 "탈퇴한 사용자"로 갱신하고 `app_user` 행은 재가입 복구를 위해 물리 삭제하지 않는다.
- 탈퇴한 사용자가 기존에 생성·작성·업로드한 공유 콘텐츠는 즉시 삭제하지 않고, `MEMBER`로 참여 중인 공유 그룹 멤버십은 물리 삭제한다.
- 탈퇴한 사용자의 활성 기기는 soft delete하고, 해당 사용자의 사진 좋아요는 물리 삭제한다.
- 동일한 Apple 계정으로 재가입하면 기존 `app_user`를 복구하되 과거 공유 그룹 멤버십은 자동 복구하지 않는다.

## 9. 컬럼 설계 요약

| 테이블 | 핵심 컬럼 |
|---|---|
| `app_user` | `apple_subject`, `display_name`, `deleted_at` |
| `refresh_token` | `app_user_id`, `token_hash`, `token_family_id`, `expires_at`, `revoked_at`, `replaced_by_refresh_token_id` |
| `invite_code_reservation` | `invite_code`, `created_at` |
| `shared_group` | `created_by_app_user_id`, `name`, `invite_code`, `deleted_at` |
| `shared_group_membership` | `shared_group_id`, `app_user_id`, `role` |
| `device` | `app_user_id`, `name`, `deleted_at` |
| `shared_group_chat_message` | `shared_group_id`, `app_user_id`, `content`, `deleted_at` |
| `shared_album` | `shared_group_id`, `created_by_app_user_id`, `name`, `deleted_at` |
| `photo` | `shared_album_id`, `uploaded_by_app_user_id`, 파일 참조 컬럼, `taken_at`, `deleted_at` |
| `photo_like` | `photo_id`, `app_user_id` |
| `photo_comment` | `photo_id`, `app_user_id`, `content`, `deleted_at` |

공통 컬럼:

- 주요 업무 테이블은 `id`, `created_at`, `updated_at`을 가진다.
- soft delete 대상 테이블은 `deleted_at`을 가진다.
- `invite_code_reservation`은 상태 변경을 기록하지 않으므로 `updated_at`을 두지 않는다.

## 10. FK와 삭제 규칙

| From | To | 관계 | 삭제 규칙 |
|---|---|---|---|
| `refresh_token.app_user_id` | `app_user.id` | N:1 | cascade |
| `shared_group.invite_code` | `invite_code_reservation.invite_code` | 1:1 | 공유 그룹 존재 중 예약 행 삭제 제한, 공유 그룹 물리 삭제 후 예약 행 삭제 |
| `shared_group.created_by_app_user_id` | `app_user.id` | N:1 | restrict |
| `shared_group_membership.shared_group_id` | `shared_group.id` | N:1 | cascade |
| `shared_group_membership.app_user_id` | `app_user.id` | N:1 | restrict |
| `device.app_user_id` | `app_user.id` | N:1 | cascade |
| `shared_group_chat_message.shared_group_id` | `shared_group.id` | N:1 | cascade |
| `shared_group_chat_message.app_user_id` | `app_user.id` | N:1 | restrict |
| `shared_album.shared_group_id` | `shared_group.id` | N:1 | cascade |
| `shared_album.created_by_app_user_id` | `app_user.id` | N:1 | restrict |
| `photo.shared_album_id` | `shared_album.id` | N:1 | cascade |
| `photo.uploaded_by_app_user_id` | `app_user.id` | N:1 | restrict |
| `photo_like.photo_id`, `photo_like.app_user_id` | `photo.id`, `app_user.id` | N:1 | photo cascade, user cascade |
| `photo_comment.photo_id`, `photo_comment.app_user_id` | `photo.id`, `app_user.id` | N:1 | photo cascade, user restrict |

## 11. Unique와 인덱스 기준

Unique:

- `app_user.apple_subject`
- `refresh_token.token_hash`
- `refresh_token.replaced_by_refresh_token_id`
- `invite_code_reservation.invite_code`
- `shared_group.invite_code`
- `shared_group_membership(shared_group_id, app_user_id)`
- `photo.object_key`
- `photo_like(photo_id, app_user_id)`

PostgreSQL 보완 인덱스:

- 공유 그룹별 방장: `shared_group_membership(shared_group_id) where role = 'HOST'`
- 사용자별 활성 기기명: `device(app_user_id, lower(btrim(name))) where deleted_at is null`
- 공유집(앨범) 사진 표시 정렬: `photo(shared_album_id, coalesce(taken_at, created_at), id) where deleted_at is null`

조회 인덱스 기준:

- 사용자의 공유 그룹 목록은 `shared_group_membership(app_user_id, created_at)`, `shared_group.deleted_at`, `app_user.deleted_at`을 조합한다.
- 공유 그룹 멤버 목록은 `shared_group_membership(shared_group_id, created_at)`을 사용한다.
- 공유 그룹 채팅 폴링은 `shared_group_chat_message(shared_group_id, deleted_at, created_at, id)`를 사용한다.
- 공유집(앨범) 목록은 `shared_album(shared_group_id, deleted_at, created_at)`을 사용한다.
- 공유집(앨범) 사진 목록은 `photo(shared_album_id, deleted_at, id)`와 표시 시각 partial expression index를 사용한다.
- 사진 댓글 목록은 `photo_comment(photo_id, deleted_at, created_at)`을 사용한다.

## 12. 검증 규칙

- 사용자명, 공유 그룹명, 공유집(앨범)명, 기기명, 채팅 메시지, 댓글은 공백 문자열을 허용하지 않는다.
- 사진 댓글은 최대 1,000자로 제한한다.
- 그룹 채팅 메시지는 최대 1,000자로 제한한다.
- 사진 파일 크기는 0보다 커야 한다.
- 사진 content type은 `image/*`만 허용한다.
- 초대 코드는 공백 문자열을 허용하지 않는다.
- 공유집(앨범)과 사진은 활성 공유 그룹 멤버십이 있는 사용자만 생성할 수 있다.
- 사진 업로드 시 대상 공유집(앨범)은 활성 상태이고 요청 사용자가 상위 공유 그룹의 활성 멤버여야 한다.

## 13. 삭제와 정리 정책

- `app_user`, `shared_group`, `device`, `shared_group_chat_message`, `shared_album`, `photo`, `photo_comment`는 soft delete한다.
- `shared_group_membership`, `photo_like`는 물리 삭제한다.
- 사용자 탈퇴 시 `app_user.deleted_at`을 기록하고 활성 Refresh Token을 폐기한다.
- 사용자 탈퇴 시 `app_user.display_name`을 "탈퇴한 사용자"로 갱신하고 `app_user` 행은 재가입 복구를 위해 물리 삭제하지 않는다.
- 사용자 탈퇴 시 방장으로 만든 공유 그룹은 soft delete하고, `MEMBER`로 참여 중인 공유 그룹 멤버십은 물리 삭제한다.
- 사용자 탈퇴 시 해당 사용자의 활성 기기는 soft delete하고 사진 좋아요는 물리 삭제한다.
- 탈퇴한 사용자가 기존에 생성·작성·업로드한 공유 콘텐츠는 즉시 삭제하지 않고 사용자 표시는 "탈퇴한 사용자"로 대체한다.
- 공유 그룹 삭제는 방장만 할 수 있고 `shared_group.deleted_at`을 기록한다.
- 공유집(앨범) 삭제는 생성자 또는 탈퇴한 생성자의 공유 그룹 방장이 할 수 있고 `shared_album.deleted_at`을 기록한다.
- 사진 원본 삭제는 업로더 또는 탈퇴한 업로더의 공유 그룹 방장이 할 수 있고 `photo.deleted_at`을 기록한다.
- 사진 댓글과 그룹 채팅 메시지는 작성자만 삭제할 수 있고 각 `deleted_at`을 기록한다.
- 사진 좋아요 취소는 `photo_like` 행을 즉시 물리 삭제한다.
- 개별 삭제한 공유집(앨범)·사진과 삭제된 공유 그룹 데이터는 30일 뒤 물리 삭제한다.
- 공유 그룹 물리 삭제 시 공유집(앨범), 사진, 댓글, 채팅 메시지, 멤버십은 FK cascade로 정리한다.
- 공유집(앨범) 물리 삭제 시 사진, 사진 좋아요, 사진 댓글은 FK cascade로 정리한다.
- Object Storage 객체 삭제가 필요한 사진은 객체 삭제가 성공했거나 삭제 재시도 작업을 기록한 뒤 DB 행을 물리 삭제한다.
- Object Storage 객체 삭제가 실패하고 재시도 작업도 기록하지 못하면 DB 행은 남겨 재시도한다.
- 공유 그룹 물리 삭제 시 해당 `invite_code_reservation` 행도 삭제해 초대 코드 점유를 해제한다.

## 14. 구현 체크리스트

- [x] 최상위 탭을 메인, 사진, 사진집, 공유로 반영했다.
- [x] 공유 위계를 공유 그룹, 공유집(앨범), 사진으로 반영했다.
- [x] `shared_house` 물리 테이블을 제거하고 공유집(앨범)을 `shared_album`으로 통합했다.
- [x] 사진이 `photo.shared_album_id`로 공유집(앨범)에 직접 속하도록 반영했다.
- [x] `shared_album_photo` 관계 테이블을 제거했다.
- [x] 그룹 채팅 메시지 저장 테이블을 추가했다.
- [x] 그룹 채팅 조회 방식을 폴링 기준으로 정리했다.
- [x] 모든 작성자·생성자·업로더 외래 키를 `app_user_id` 계열로 설계했다.
- [x] 공유 그룹별 사용자 멤버십 unique를 DBML에 정의했다.
- [x] 공유 그룹별 방장 partial unique를 PostgreSQL 보완 SQL에 정의한다.
- [x] `shared_group.created_by_app_user_id`를 두고 별도의 `host_app_user_id`는 두지 않았다.
- [x] `invite_code_reservation`을 추가하고 `shared_group.invite_code`가 예약 코드를 참조하도록 했다.
- [x] `shared_album.shared_group_id`에 필수 외래 키를 적용했다.
- [x] `photo.shared_album_id`에 필수 외래 키를 적용했다.
- [x] 로컬 사진집 탭 데이터를 서버 모델에 포함하지 않았다.
- [x] 사진 좋아요는 단순 좋아요로 반영했다.
- [x] 날짜별 사진 그룹은 `taken_at` nullable 컬럼과 표시 시각 인덱스로 반영했다.
- [x] 기기 태그는 사용자 단위 표시 정보로 반영했다.
- [x] `photo_like(photo_id, app_user_id)`에 unique 제약을 적용했다.
- [x] 사진·공유집(앨범)의 개별 삭제 후 30일 정리를 위한 기준을 정의했다.
- [x] 탈퇴한 사용자의 공유 콘텐츠 표시를 "탈퇴한 사용자"로 대체하도록 정의했다.
- [x] 탈퇴한 생성자·업로더의 공유집(앨범)·사진 삭제 권한을 공유 그룹 방장에게 위임했다.
- [x] 사용자 탈퇴 시 기기 soft delete와 사진 좋아요 물리 삭제 정책을 정의했다.
- [x] 활성 멤버십 기준에 사용자 soft delete 상태를 포함했다.

## 15. 남은 구현 과제

1. PostgreSQL `api_idempotency_record` 같은 기술 테이블 필요 여부 검토
2. 공유 그룹 채팅 폴링 API의 커서 응답 형식 확정
3. 사진을 다른 공유집(앨범)으로 옮기는 기능이 필요한지 제품 정책 확인
4. 그룹 채팅 읽음 상태와 알림 정책 필요 여부 확인
5. 공유 그룹 삭제와 Object Storage 객체 정리 배치 통합 테스트 작성
