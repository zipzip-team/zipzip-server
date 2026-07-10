# Zipzip 데이터 모델링

## 1. 모델링 원칙

- 주요 테이블의 PK는 애플리케이션에서 생성한 `uuid`를 사용한다.
- 테이블명과 DB 컬럼명은 `snake_case`를 사용한다.
- Java/Kotlin 필드와 API JSON 필드는 `camelCase`를 사용한다.
- 삭제 정책은 데이터 성격에 따라 soft delete, 상태 변경, 물리 삭제를 구분한다.
- 모든 주요 업무 테이블은 `created_at`, `updated_at`을 가진다. 예약 테이블은 예외를 둔다.
- soft delete 대상 테이블은 `deleted_at`을 가진다.
- 모든 DB `timestamptz` 컬럼은 JPA 엔티티에서 `java.time.Instant`로 매핑한다.
- 하단 네비게이션의 공유 탭은 DB 엔티티로 만들지 않는다.
- 메인, 사진, 사진집은 로컬 중심 기능이다.
- 공유 탭의 서버 위계는 공유 그룹, 공유집(앨범), 사진 순서이다.
- 기존 `shared_house` 물리 테이블은 두지 않고 공유집(앨범)을 `shared_album`으로 저장한다.
- 사진은 공유 그룹에 직접 속하지 않고, `shared_album_photo`를 통해 하나 이상의 공유집(앨범)에 속한다. 공유 위계는 공유 그룹 > 공유집(앨범) > 사진 순서를 항상 지킨다.
- 사진은 항상 1개 이상의 공유집(앨범)에 속해야 한다. 마지막 공유집(앨범) 소속이 없어지면(공유집(앨범) 삭제 또는 명시적 제거) 사진 원본도 함께 soft delete한다.
- 공유 플로우에서만 서버에 사진을 업로드한다.
- 이미지 파일은 OCI Object Storage에 저장한다.
- DB에는 Object Storage 참조에 필요한 최소 파일 정보만 저장한다.
- 업로드용 presigned URL 발급은 `photo_upload_reservation`에 발급 대상(사용자·공유집(앨범))과 만료 시각을 기록하고, 완료 등록은 이 예약 행을 검증·소진해 발급 대상이 아닌 `objectKey` 등록과 재사용을 막는다.
- 촬영 기기는 별도 엔티티로 관리하지 않고, iOS가 EXIF에서 추출해 전달한 촬영 기기명을 `photo.device_model` 문자열로 저장한다.
- 공유 그룹 하나를 하나의 채팅방으로 사용하며 별도 `chat_room` 테이블을 두지 않는다. 일반 채팅 메시지는 `shared_group_chat_message`, 사진 댓글은 `photo_comment`에 저장하고, 채팅 조회 시 두 데이터를 시간순 타임라인으로 병합한다. 1차 구현은 폴링으로 조회한다.
- 날짜별 사진 그룹은 촬영일 기준으로 표시하고 촬영일이 없으면 생성 시각을 사용한다.
- 공유집(앨범) 사진 수는 `shared_album_photo`와 조인한 활성 사진 기준으로 실시간 count한다.
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
- Zipzip 서비스 탈퇴 시 사용자의 사진 좋아요는 물리 삭제한다.
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
        └── 앨범-사진 매핑(shared_album_photo)
            └── 사진(photo)
                ├── 사진 좋아요(photo_like)
                └── 사진 댓글(photo_comment)
```

공유 그룹 카드 하나는 `shared_group` 한 행에 해당한다.
공유집(앨범) 카드 하나는 `shared_album` 한 행에 해당한다.
사진 원본은 `photo` 한 행에 해당하며 공유 그룹에 직접 속하지 않고, `shared_album_photo`를 통해서만 하나 이상의 공유집(앨범)에 속한다.

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
| 그룹 채팅 메시지 | `shared_group_chat_message` | 필요 | 공유 그룹 채팅 타임라인에 포함되는 일반 메시지를 저장한다. |
| 공유집(앨범) | `shared_album` | 필요 | 공유 그룹 안에서 사진을 담는 하위 단위를 저장한다. |
| 사진 업로드 예약 원장 | `photo_upload_reservation` | 필요 | 발급한 업로드 `objectKey`를 요청 사용자·대상 공유집(앨범)과 함께 점유해, 완료 등록 시 발급 대상 검증과 재사용 방지 근거를 제공한다. |
| 사진 | `photo` | 필요 | 공유 그룹에 업로드된 사진 원본의 Object Storage 참조 정보를 저장한다. |
| 앨범-사진 매핑 | `shared_album_photo` | 필요 | 사진과 공유집(앨범)의 N:M 소속 관계를 저장한다. |
| 사진 좋아요 | `photo_like` | 필요 | 사용자별 사진 좋아요 상태를 저장한다. |
| 사진 댓글 | `photo_comment` | 필요 | 단일 사진에 달리는 댓글을 저장한다. |

1차 모델은 12개 테이블이다.
별도 `shared_house`, `chat_room`, `chat_message` 테이블은 두지 않는다.

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

### 5.6 `shared_group_chat_message`

공유 그룹 안에서 작성한 일반 채팅 메시지를 저장한다.
메시지 작성과 조회는 공유 그룹 활성 멤버십을 요구한다.
MVP는 메시지 작성과 조회만 제공하며, 공유 그룹 물리 삭제 시 FK cascade로 정리한다.
공유 그룹 자체가 채팅방 식별자이므로 별도 채팅방 엔티티를 참조하지 않는다.
채팅 타임라인 조회는 이 테이블의 일반 메시지와, 같은 공유 그룹의 활성 사진에 연결된 `photo_comment`를 병합한다. 타임라인 정렬과 커서는 `created_at`, 항목 타입, 항목 ID를 함께 사용해 안정성을 보장한다.

### 5.7 `shared_album`

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

### 5.8 `photo_upload_reservation`

PHOTO-02에서 발급한 업로드 `objectKey`를 요청 사용자·대상 공유집(앨범)·만료 시각과 함께 예약하는 원장을 저장한다.

필요한 이유:

- PHOTO-03 완료 등록 시 `objectKey`가 실제로 이 사용자·이 공유집(앨범)에 발급됐는지 검증하는 근거 제공
- 같은 `objectKey`로 완료 등록을 반복(재사용·재생)하는 것을 방지
- 만료된 미완료 예약과 그에 대응하는 Object Storage 객체를 정기 스윕으로 정리하는 근거 제공

`invite_code_reservation`과 같은 이유로 예약 행의 상태 변경을 기록하지 않고 `objectKey` 자체를 PK로 사용하며 `updated_at`을 두지 않는다.
PHOTO-03 완료 등록에 성공하면 해당 예약 행을 즉시 물리 삭제한다. 따라서 예약 행이 없는 상태는 "발급된 적 없음"과 "이미 등록에 사용함"을 구분하지 않는다.

### 5.9 `photo`

사진 원본의 Object Storage 참조 정보를 저장한다. 공유 그룹에 직접 속하지 않고 `shared_album_photo`를 통해서만 공유집(앨범)에 속한다.

필요한 이유:

- 업로더 연결, 촬영 기기명 저장
- 원본·썸네일 이미지 Object Storage 객체 키 저장
- 표시와 정렬에 사용할 촬영일시와 위치·크기 메타데이터 저장
- 사진 원본 수정·삭제 권한 기준 제공
- 사진 원본 삭제 상태 관리
- 비동기 썸네일 생성 진행 상태 관리

사진이 실제로 담기는 공유집(앨범)은 `shared_album_photo`로 표현하며, 같은 사진을 여러 공유집(앨범)에 중복 업로드 없이 담을 수 있다.
사진은 항상 1개 이상의 공유집(앨범)에 속해야 한다. 사진의 "소속 공유 그룹"이 필요하면 `shared_album_photo`로 연결된 공유집(앨범)의 `shared_group_id`를 조인해 구한다(같은 사진의 모든 공유집(앨범)은 항상 같은 공유 그룹에 속한다).
원본과 썸네일은 URL이 아니라 Object Storage 객체 키(`original_object_key`, `thumbnail_object_key`)로 저장하고, API는 조회 시점에 presigned URL을 발급한다.
썸네일은 업로드 완료 등록 직후 비동기로 생성하며, 진행 상태는 `thumbnail_status`(`PENDING`, `READY`, `FAILED`)로 관리한다.
사진 원본 수정·삭제는 원칙적으로 업로더가 할 수 있고, 업로더가 탈퇴한 사용자인 경우 삭제는 상위 공유 그룹의 방장이 할 수 있다.
로컬 저장공간 또는 로컬 사진집에서 공유집(앨범)으로 사진을 불러오는 플로우는 사진 업로드로 처리한다.
공유 사진 또는 공유 앨범을 로컬 저장공간으로 복사하는 플로우는 다운로드이며 서버 엔티티를 생성하지 않는다.

### 5.10 `shared_album_photo`

사진과 공유집(앨범)의 N:M 소속 관계를 저장한다.

필요한 이유:

- 하나의 사진을 여러 공유집(앨범)에 중복 업로드 없이 담기 위한 관계 표현
- 공유집(앨범)별 사진 목록과 사진 수 집계의 조회 범위 제공
- 사진이 항상 1개 이상의 공유집(앨범)에 속하도록 보장하는 기준 제공

같은 `shared_album_id`와 `photo_id` 조합은 유일하다.
사진을 공유집(앨범)에 추가·제거하는 권한은 사진이 속한 공유 그룹의 활성 멤버십을 기준으로 한다.
공유집(앨범)을 삭제하면 그 공유집(앨범)의 매핑을 모두 물리 삭제한다. 이때 다른 활성 공유집(앨범)에도 속한 사진은 원본을 유지하고, 방금 삭제한 공유집(앨범)이 마지막 소속이었던 사진은 함께 soft delete한다.
사진을 특정 공유집(앨범)에서 명시적으로 제거할 때도 같은 규칙을 적용한다: 다른 공유집(앨범)에 남아 있으면 매핑만 제거하고, 마지막 소속이면 사진 원본도 soft delete한다.

### 5.11 `photo_like`

사용자가 특정 사진에 좋아요를 누른 상태를 저장한다.
좋아요 취소 시 `photo_like` 행을 물리 삭제한다.
사용자 탈퇴 시 해당 사용자의 `photo_like` 행도 물리 삭제해 활성 좋아요 수에 탈퇴 사용자를 포함하지 않는다.

### 5.12 `photo_comment`

단일 사진에 달리는 댓글을 저장한다.
댓글 작성과 조회는 사진이 속한 공유 그룹 활성 멤버십을 요구한다.
사진 댓글은 사진 상세의 댓글 목록뿐 아니라, 사진이 속한 공유 그룹의 채팅 타임라인에도 일반 메시지와 함께 시간순으로 표시한다. 사진이 soft delete되었거나 대상 공유 그룹에 활성 소속이 없으면 타임라인에서는 제외한다.

## 6. 제외 테이블

| 후보 | 제외 이유 |
|---|---|
| `shared_house` | 공유집을 별도 물리 테이블로 두지 않고 `shared_album`으로 저장한다. |
| `photo_book` | 로컬 중심 기능이며 서버와 자동 동기화하지 않는다. |
| `photo_metadata` | iOS가 이미지 EXIF에서 추출해 전달하는 촬영일시·위치·크기 메타데이터를 `photo`의 nullable 컬럼에 직접 저장하고 별도 메타데이터 테이블은 두지 않는다. |
| `chat_room`, `chat_message` | 공유 그룹 하나가 채팅방 하나를 의미하므로 별도 채팅방 식별자가 필요 없다. 일반 메시지는 `shared_group_chat_message`, 사진 댓글은 `photo_comment`에 저장하고 조회 시 병합한다. |
| `shared_album_like` | 좋아요 UI는 사진 중심이며 앨범 좋아요 근거가 부족하다. |
| `device` | 사용자가 직접 등록·관리하는 기기 엔티티였으나, 촬영 기기명은 사진마다 EXIF에서 그대로 얻을 수 있어 `photo.device_model` 문자열 컬럼으로 대체했다. |

## 7. 관계 요약

```text
app_user
├── refresh_token
├── shared_group
├── shared_group_membership
├── shared_group_chat_message
├── shared_album
├── photo_upload_reservation
├── photo
├── photo_like
└── photo_comment

shared_group
├── shared_group_membership
├── shared_group_chat_message
└── shared_album

shared_album
├── photo_upload_reservation
└── shared_album_photo

photo
├── shared_album_photo
├── photo_like
└── photo_comment
```

- 한 공유 그룹은 여러 멤버십을 가질 수 있다.
- 한 공유 그룹은 하나의 `HOST` 멤버십만 가질 수 있다.
- 한 공유 그룹은 여러 공유집(앨범)을 가질 수 있다. 사진은 공유 그룹에 직접 속하지 않는다.
- 한 공유집(앨범)은 사진 없이 존재하거나 `shared_album_photo`를 통해 여러 사진을 가질 수 있다.
- 한 공유집(앨범)과 한 사용자는 여러 `photo_upload_reservation`을 가질 수 있다. 완료 등록에 성공하거나 만료되면 해당 예약 행은 없어진다.
- 한 사진은 `shared_album_photo`를 통해 하나 이상의 공유집(앨범)에 속해야 한다. 마지막 소속 공유집(앨범)이 없어지면 사진도 함께 soft delete한다.
- 한 사진은 여러 좋아요와 댓글을 가질 수 있다.
- 한 사용자는 여러 공유 그룹에 참여할 수 있다.
- 한 사용자는 여러 공유집(앨범)을 생성하고, 여러 사진을 업로드할 수 있다.
- 한 사용자는 여러 그룹 채팅 메시지와 사진 댓글을 작성할 수 있다.
- 공유집(앨범) 사진 수는 활성 `shared_album_photo` 매핑과 활성 사진 기준 실시간 count로 계산한다.
- 작성자·생성자·업로더는 `app_user` FK로 보존하고, 공유 그룹 내부 권한은 활성 공유 그룹 멤버십을 서비스 계층에서 검증한다.
- 탈퇴한 작성자·생성자·업로더의 화면 표시는 "탈퇴한 사용자"로 대체한다.

## 8. ERD 설계 요약

- 1차 ERD 대상은 `app_user`, `refresh_token`, `invite_code_reservation`, `shared_group`, `shared_group_membership`, `shared_group_chat_message`, `shared_album`, `photo_upload_reservation`, `photo`, `shared_album_photo`, `photo_like`, `photo_comment` 12개 테이블이다.
- 하단 네비게이션의 공유 탭은 테이블로 만들지 않는다.
- 사진집 탭의 로컬 데이터는 서버 모델에 포함하지 않는다.
- 초대 코드, 멤버십, 방장 역할, 관리, 그룹 채팅은 공유 그룹에 속한다.
- 공유집(앨범)은 공유 그룹 아래에서 참여자들이 생성하는 사진 묶음이다.
- 사진 원본은 공유 그룹에 직접 속하지 않고, `shared_album_photo`로 하나 이상의 공유집(앨범)에 속한다.
- `photo_upload_reservation`은 업로드 URL 발급과 완료 등록 사이를 잇는 예약 원장이며, 완료 등록이 발급 대상이 아닌 사용자·공유집(앨범)이나 재사용된 `objectKey`를 받아들이지 않도록 한다.
- 공유 그룹 하나를 채팅방으로 사용한다. 일반 메시지는 `shared_group_chat_message`, 사진 댓글은 `photo_comment`에 저장하며 1차 구현은 두 데이터를 병합한 폴링 타임라인 조회를 사용한다.
- 사진 댓글은 사진 상세 기능에 속하면서 해당 공유 그룹의 채팅 타임라인에도 시간순으로 표시한다.
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
- 탈퇴한 사용자의 사진 좋아요는 물리 삭제한다.
- 동일한 Apple 계정으로 재가입하면 기존 `app_user`를 복구하되 과거 공유 그룹 멤버십은 자동 복구하지 않는다.

## 9. 컬럼 설계 요약

| 테이블 | 핵심 컬럼 |
|---|---|
| `app_user` | `apple_subject`, `display_name`, `deleted_at` |
| `refresh_token` | `app_user_id`, `token_hash`, `token_family_id`, `expires_at`, `revoked_at`, `replaced_by_refresh_token_id` |
| `invite_code_reservation` | `invite_code`, `created_at` |
| `shared_group` | `created_by_app_user_id`, `name`, `invite_code`, `deleted_at` |
| `shared_group_membership` | `shared_group_id`, `app_user_id`, `role` |
| `shared_group_chat_message` | `shared_group_id`, `app_user_id`, `content` |
| `shared_album` | `shared_group_id`, `created_by_app_user_id`, `name`, `deleted_at` |
| `photo_upload_reservation` | `object_key`, `shared_album_id`, `requested_by_app_user_id`, `expires_at`, `created_at` |
| `photo` | `uploaded_by_app_user_id`, `device_model`, `original_object_key`, `thumbnail_object_key`, `thumbnail_status`, `taken_at`, 위치·크기 메타데이터 컬럼, `deleted_at` |
| `shared_album_photo` | `shared_album_id`, `photo_id` |
| `photo_like` | `photo_id`, `app_user_id` |
| `photo_comment` | `photo_id`, `app_user_id`, `content` |

공통 컬럼:

- 주요 업무 테이블은 `id`, `created_at`, `updated_at`을 가진다.
- soft delete 대상 테이블은 `deleted_at`을 가진다.
- `invite_code_reservation`, `photo_upload_reservation`은 상태 변경을 기록하지 않으므로 `updated_at`을 두지 않는다.
- `shared_group_chat_message`, `photo_comment`는 즉시 물리 삭제하는 테이블이라 `deleted_at`을 두지 않는다.

## 10. FK와 삭제 규칙

| From | To | 관계 | 삭제 규칙 |
|---|---|---|---|
| `refresh_token.app_user_id` | `app_user.id` | N:1 | cascade |
| `shared_group.invite_code` | `invite_code_reservation.invite_code` | 1:1 | 공유 그룹 존재 중 예약 행 삭제 제한, 공유 그룹 물리 삭제 후 예약 행 삭제 |
| `shared_group.created_by_app_user_id` | `app_user.id` | N:1 | restrict |
| `shared_group_membership.shared_group_id` | `shared_group.id` | N:1 | cascade |
| `shared_group_membership.app_user_id` | `app_user.id` | N:1 | restrict |
| `shared_group_chat_message.shared_group_id` | `shared_group.id` | N:1 | cascade |
| `shared_group_chat_message.app_user_id` | `app_user.id` | N:1 | restrict |
| `shared_album.shared_group_id` | `shared_group.id` | N:1 | cascade |
| `shared_album.created_by_app_user_id` | `app_user.id` | N:1 | restrict |
| `photo_upload_reservation.shared_album_id` | `shared_album.id` | N:1 | cascade |
| `photo_upload_reservation.requested_by_app_user_id` | `app_user.id` | N:1 | restrict |
| `photo.uploaded_by_app_user_id` | `app_user.id` | N:1 | restrict |
| `shared_album_photo.shared_album_id` | `shared_album.id` | N:1 | cascade |
| `shared_album_photo.photo_id` | `photo.id` | N:1 | cascade |
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
- `photo.original_object_key`
- `shared_album_photo(shared_album_id, photo_id)`
- `photo_like(photo_id, app_user_id)`

PostgreSQL 보완 인덱스:

- 공유 그룹별 방장: `shared_group_membership(shared_group_id) where role = 'HOST'`
- 사진 표시 정렬: `photo(coalesce(taken_at, created_at), id) where deleted_at is null`

조회 인덱스 기준:

- 사용자의 공유 그룹 목록은 `shared_group_membership(app_user_id, created_at)`, `shared_group.deleted_at`, `app_user.deleted_at`을 조합한다.
- 공유 그룹 멤버 목록은 `shared_group_membership(shared_group_id, created_at)`을 사용한다.
- 공유 그룹 채팅 타임라인은 일반 메시지 구간에 `shared_group_chat_message(shared_group_id, created_at, id)`를 사용하고, 사진 댓글은 `photo_comment`와 사진·앨범 소속 관계를 조인해 병합한다.
- 공유집(앨범) 목록은 `shared_album(shared_group_id, deleted_at, created_at)`을 사용한다.
- 공유집(앨범) 사진 목록은 `shared_album_photo(shared_album_id, created_at)`으로 매핑을 조회한 뒤 `photo`와 조인해 표시 시각으로 정렬한다.
- 사진 댓글 목록은 `photo_comment(photo_id, created_at)`을 사용한다.
- 만료된 미완료 업로드 예약 스윕은 `photo_upload_reservation(expires_at)`을 사용한다.

## 12. 검증 규칙

- 사용자명, 공유 그룹명, 공유집(앨범)명, 채팅 메시지, 댓글은 공백 문자열을 허용하지 않는다.
- 사진 댓글은 최대 1,000자로 제한한다.
- 그룹 채팅 메시지는 최대 1,000자로 제한한다.
- 초대 코드는 공백 문자열을 허용하지 않는다.
- 공유집(앨범)과 사진은 활성 공유 그룹 멤버십이 있는 사용자만 생성할 수 있다.
- 사진 업로드 시 대상 공유집(앨범)은 활성 상태이고 요청 사용자가 상위 공유 그룹의 활성 멤버여야 한다.
- 같은 사진을 같은 공유집(앨범)에 중복으로 담을 수 없다.
- 사진은 항상 1개 이상의 공유집(앨범)에 속해야 한다. 이 불변식은 DB 제약이 아니라 서비스 계층(공유집(앨범) 삭제, 사진 제거 처리)에서 강제한다.
- 사진 업로드 완료 등록은 `objectKey`에 대응하는 `photo_upload_reservation`이 요청 사용자·요청 경로 공유집(앨범)과 일치하고 만료되지 않았을 때만 허용한다. 일치하지 않으면 발급받은 적 없는 것과 동일하게 취급한다.

## 13. 삭제와 정리 정책

- `app_user`, `shared_group`, `shared_album`, `photo`는 soft delete한다.
- `shared_group_membership`, `shared_album_photo`, `photo_like`, `shared_group_chat_message`, `photo_comment`, `photo_upload_reservation`은 물리 삭제한다.
- 사용자 탈퇴 시 `app_user.deleted_at`을 기록하고 활성 Refresh Token을 폐기한다.
- 사용자 탈퇴 시 `app_user.display_name`을 "탈퇴한 사용자"로 갱신하고 `app_user` 행은 재가입 복구를 위해 물리 삭제하지 않는다.
- 사용자 탈퇴 시 방장으로 만든 공유 그룹은 soft delete하고, `MEMBER`로 참여 중인 공유 그룹 멤버십은 물리 삭제한다.
- 사용자 탈퇴 시 사진 좋아요는 물리 삭제한다.
- 탈퇴한 사용자가 기존에 생성·작성·업로드한 공유 콘텐츠는 즉시 삭제하지 않고 사용자 표시는 "탈퇴한 사용자"로 대체한다.
- 공유 그룹 삭제는 방장만 할 수 있고 `shared_group.deleted_at`을 기록한다.
- 공유집(앨범) 삭제는 생성자 또는 탈퇴한 생성자의 공유 그룹 방장이 할 수 있고 `shared_album.deleted_at`을 기록한다.
- 사진 원본 삭제는 업로더 또는 탈퇴한 업로더의 공유 그룹 방장이 할 수 있고 `photo.deleted_at`을 기록한다.
- 사진 댓글과 그룹 채팅 메시지는 작성자만 삭제할 수 있고, `deleted_at` 기록 없이 행을 즉시 물리 삭제한다.
- 사진 좋아요 취소는 `photo_like` 행을 즉시 물리 삭제한다.

**공유집(앨범)에서 사진이 빠지는 경우(공유집(앨범) 삭제, 사진을 특정 공유집(앨범)에서 명시적으로 제거)** 는 같은 규칙을 따른다.

1. 대상 `shared_album_photo` 매핑을 즉시 물리 삭제한다.
2. 매핑이 없어진 각 사진에 다른 활성 공유집(앨범) 매핑이 남아 있는지 확인한다.
3. 남아 있으면 사진 원본은 그대로 둔다. 남아 있지 않으면(방금 없앤 매핑이 마지막 소속이었으면) 사진도 함께 soft delete(`photo.deleted_at`)한다.

공유집(앨범) 삭제 시에는 그 공유집(앨범)에 속했던 모든 사진에 대해 이 판단을 한 번에 수행한다. 공유 그룹 삭제 시에는 그 그룹의 활성 공유집(앨범)과 그 매핑으로 식별한 활성 사진에 같은 `deleted_at`을 기록한다. 이때 그룹 삭제의 물리 정리 전까지 `shared_album_photo` 매핑은 유지한다. 사진이 공유 그룹에 직접 FK를 갖지 않으므로, 이 매핑이 30일 뒤 Object Storage 객체를 먼저 정리할 사진 대상을 보장한다.

- 개별 삭제한 공유집(앨범)·사진과 삭제된 공유 그룹 데이터는 30일 뒤 물리 삭제한다. 그룹 정리 배치는 PostgreSQL 행 잠금으로 후보를 한 인스턴스만 처리하며, 그룹의 `shared_album_photo` 매핑으로 찾은 사진마다 원본·썸네일 Object Storage 객체를 먼저 삭제한다. 성공한 사진의 매핑·댓글·좋아요·사진 행을 물리 삭제한 뒤에만 공유 그룹을 삭제한다.
- 그룹 사진 중 하나라도 Object Storage 삭제에 실패하면 해당 트랜잭션을 롤백해 공유 그룹과 초대 코드 예약을 남기고 다음 배치에서 재시도한다.
- 공유 그룹 물리 삭제 시 남아 있는 공유집(앨범), 채팅 메시지, 멤버십은 FK cascade로 정리한다.
- Object Storage 객체 삭제가 필요한 사진은 원본·썸네일 객체 삭제가 성공했거나 삭제 재시도 작업을 기록한 뒤 DB 행을 물리 삭제한다.
- Object Storage 객체 삭제가 실패하고 재시도 작업도 기록하지 못하면 DB 행은 남겨 재시도한다.
- 공유 그룹 물리 삭제 시 해당 `invite_code_reservation` 행도 삭제해 초대 코드 점유를 해제한다.
- 사진 업로드 완료 등록에 성공하면 해당 `photo_upload_reservation` 행을 같은 트랜잭션에서 물리 삭제한다.
- 만료되었지만 완료 등록에 쓰이지 않은 `photo_upload_reservation` 행은 정기 스윕이 물리 삭제하고, 대응하는 Object Storage 객체가 남아 있으면 함께 정리한다.

## 14. 구현 체크리스트

- [x] 최상위 탭을 메인, 사진, 사진집, 공유로 반영했다.
- [x] 공유 위계를 공유 그룹, 공유집(앨범), 사진으로 반영했다.
- [x] `shared_house` 물리 테이블을 제거하고 공유집(앨범)을 `shared_album`으로 통합했다.
- [x] 사진은 공유 그룹에 직접 속하지 않고 `shared_album_photo`를 통해서만 공유집(앨범)에 속하도록 반영했다(공유 위계 공유 그룹 > 공유집(앨범) > 사진 유지).
- [x] `shared_album_photo` 관계 테이블을 추가해 사진과 공유집(앨범)을 N:M으로 연결했다.
- [x] 사진이 항상 1개 이상의 공유집(앨범)에 속하도록 하는 불변식과, 마지막 소속이 없어질 때 사진을 soft delete하는 캐스케이드 규칙을 정리했다.
- [x] 그룹 채팅 메시지 저장 테이블을 추가했다.
- [x] 그룹 채팅 조회 방식을 폴링 기준으로 정리했다.
- [x] 모든 작성자·생성자·업로더 외래 키를 `app_user_id` 계열로 설계했다.
- [x] 공유 그룹별 사용자 멤버십 unique를 DBML에 정의했다.
- [x] 공유 그룹별 방장 partial unique를 PostgreSQL 보완 SQL에 정의한다.
- [x] `shared_group.created_by_app_user_id`를 두고 별도의 `host_app_user_id`는 두지 않았다.
- [x] `invite_code_reservation`을 추가하고 `shared_group.invite_code`가 예약 코드를 참조하도록 했다.
- [x] `shared_album.shared_group_id`에 필수 외래 키를 적용했다.
- [x] `shared_album_photo(shared_album_id, photo_id)`에 unique 제약을 적용했다.
- [x] 로컬 사진집 탭 데이터를 서버 모델에 포함하지 않았다.
- [x] 사진 좋아요는 단순 좋아요로 반영했다.
- [x] 날짜별 사진 그룹은 `taken_at` nullable 컬럼과 표시 시각 인덱스로 반영했다.
- [x] 사진 촬영 기기명은 별도 엔티티로 관리하지 않고 `photo.device_model` nullable 문자열 컬럼으로 반영했다.
- [x] 사진 위치정보는 `latitude`, `longitude`, `location_name`, `is_inferred` 컬럼으로 반영했다.
- [x] `photo_like(photo_id, app_user_id)`에 unique 제약을 적용했다.
- [x] 사진 원본·썸네일을 URL이 아닌 Object Storage 객체 키(`original_object_key`, `thumbnail_object_key`)로 저장하도록 반영했다.
- [x] `photo.original_object_key`에 unique 제약을 적용했다.
- [x] 비동기 썸네일 생성 진행 상태를 `photo.thumbnail_status`(`PENDING`/`READY`/`FAILED`)로 반영했다.
- [x] `photo_upload_reservation`을 추가해 업로드 URL 발급 대상(사용자·공유집(앨범))과 재사용 여부를 완료 등록에서 검증할 수 있도록 했다.
- [x] 사진 댓글과 그룹 채팅 메시지를 soft delete에서 즉시 물리 삭제로 전환하고 `deleted_at` 컬럼을 제거했다.
- [x] 사진·공유집(앨범)의 개별 삭제 후 30일 정리를 위한 기준을 정의했다.
- [x] 탈퇴한 사용자의 공유 콘텐츠 표시를 "탈퇴한 사용자"로 대체하도록 정의했다.
- [x] 탈퇴한 생성자·업로더의 공유집(앨범)·사진 삭제 권한을 공유 그룹 방장에게 위임했다.
- [x] 사용자 탈퇴 시 사진 좋아요 물리 삭제 정책을 정의했다.
- [x] 활성 멤버십 기준에 사용자 soft delete 상태를 포함했다.
- [x] 공유 그룹 삭제 시 하위 공유집(앨범)·사진을 함께 soft delete하고, Object Storage 우선 정리 뒤 그룹·초대 코드 예약을 물리 삭제하는 30일 배치를 반영했다.

## 15. 남은 구현 과제

1. 사진을 여러 공유집(앨범)에 추가·제거할 때, 마지막 소속 제거가 원본 삭제로 이어진다는 점을 iOS 확인 UX에 반영
2. 그룹 채팅 읽음 상태와 알림 정책 필요 여부 확인

구현 완료 항목:

- PostgreSQL `api_idempotency_record` 마이그레이션과 만료 레코드 정리 배치
- 공유 그룹 채팅 폴링 API의 불투명 cursor 응답 형식
- `PHOTO-07`/`PHOTO-08` 사진 추가·제거 API
- 썸네일 원본 다운로드·리사이즈·재업로드·실패 재시도 스윕
- 만료된 `photo_upload_reservation`과 대응 Object Storage 객체 정리 스윕
