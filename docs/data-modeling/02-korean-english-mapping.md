# Zipzip 한글-영어 용어 매핑

## 1. 문서 목적

이 문서는 `01-domain-terminology.md`에서 확정한 한글 업무 용어의 영어 표준명을 정의한다.
여기서 정한 영어명은 이후 데이터 표준화, API 명세, ERD, 테이블명, 컬럼명, 코드값 설계의 기준으로 사용한다.

## 2. 매핑 원칙

- 한글 표준 용어를 기준으로 영어명을 정한다.
- 영어명은 소문자 `snake_case`를 기본 표기법으로 사용한다.
- API 경로에서는 필요 시 복수형과 `kebab-case`를 사용할 수 있다.
- 역할 코드, 상태 코드 같은 코드값은 대문자 `UPPER_SNAKE_CASE`를 사용한다.
- 하단 네비게이션의 공유 탭과 서버 엔티티인 공유 그룹을 구분한다.
- `사진집`은 로컬 중심 탭이고, 서버에 저장되는 공유 공간의 사진 묶음은 `앨범`으로 구분한다.
- `공유집`과 `앨범`은 같은 서버 하위 공간을 가리키며, 물리 테이블명은 `shared_album`을 사용한다.
- `사용자`와 `멤버`는 의미가 다르므로 서로 다른 영어명을 사용한다.

## 3. 핵심 용어 매핑

| 구분 | 한글 표준 용어 | 영어 표준명 | 코드/값 예시 | 사용 위치 | 비고 |
|---|---|---|---|---|---|
| 사람 | 사용자 | `user` | - | API, 문서, 개념 | Apple 로그인 후 서버에 등록된 사람 |
| 사람 | 사용자 테이블 | `app_user` | - | 테이블 | `user` 예약어/ORM 충돌 가능성을 피하기 위한 테이블명 |
| 화면 | 공유 탭 | `share_tab` | - | 화면, 문서 | 하단 네비게이션의 공유 탭. DB 엔티티로 사용하지 않음 |
| 화면 | 사진집 | `photo_book` | - | 화면, 문서 | 로컬 중심 탭. DB 엔티티로 사용하지 않음 |
| 공유 공간 | 공유 그룹 | `shared_group` | - | 테이블, 컬럼, API | 초대 코드, 멤버십, 채팅, 공유집(앨범)의 기준이 되는 최상위 공유 공간 |
| 관계 | 공유 그룹 멤버십 | `shared_group_membership` | - | 테이블, 관계, 권한 설명 | 사용자와 공유 그룹 사이의 참여 관계 |
| 공유 공간 | 공유집(앨범) | `shared_album` | - | 테이블, 컬럼, API | 공유 그룹 안에서 사진을 담는 하위 공간 |
| 미디어 | 사진 | `photo` | - | 테이블, 컬럼, API | 공유 그룹에 업로드된 사진 원본 |
| 관계 | 앨범-사진 매핑 | `shared_album_photo` | - | 테이블 | 사진과 공유집(앨범)의 N:M 소속 관계 |
| 미디어 | 촬영일시 | `taken_at` | - | 컬럼, API | iOS가 이미지 EXIF에서 추출해 전달하는 nullable 값. 없으면 `created_at`을 표시·정렬 기준으로 사용 |
| 관계 | 공유 그룹·공유집(앨범) 생성자 | `created_by_app_user_id` | - | 컬럼, API | 생성자 이력과 원칙적 삭제 권한 기준 |
| 관계 | 사진 업로더 | `uploaded_by_app_user_id` | - | 컬럼, API | 사진 원본 수정·삭제의 원칙적 권한 기준 |
| 사진 반응 | 사진 좋아요 | `photo_like` | - | 테이블, 컬럼, API | 사용자가 특정 사진에 좋아요를 누른 상태 |
| 사진 댓글 | 사진 댓글 | `photo_comment` | - | 테이블, 컬럼, API | 단일 사진에 달리는 댓글 |
| 채팅 | 그룹 채팅 메시지 | `shared_group_chat_message` | - | 테이블, 컬럼, API | 공유 그룹 안에서 작성하는 일반 채팅 메시지 |
| 파일 | 이미지 파일 | `image_file` | - | Object Storage, 업로드 API | 실제 저장 대상 파일 |
| 역할 | 방장 | `host` | `HOST` | 역할 코드, 권한 검사 | 공유 그룹을 만든 사용자 |
| 역할 | 멤버 | `member` | `MEMBER` | 역할 코드, 권한 검사 | 공유 그룹에 초대받아 참여한 사용자 |
| 초대 | 초대 코드 | `invite_code` | - | 컬럼, API | 공유 그룹 참여 코드. 공유 그룹이 존재하는 동안 만료·재발급·재사용 없음 |
| 초대 | 초대 코드 예약 원장 | `invite_code_reservation` | - | 테이블 | 발급한 초대 코드의 점유를 관리하는 서버 내부 예약 테이블 |
| 인증 | Apple 로그인 | `apple_login` | - | API, 문서 | 로그인 성공 후 자체 JWT 발급 |
| 인증 | Access Token | `access_token` | - | API, 문서 | 서버 자체 발급 JWT |
| 인증 | Refresh Token | `refresh_token` | - | API, 문서 | 서버 자체 발급 JWT |
| 인증 | 토큰 계열 | `token_family_id` | - | 컬럼 | 회전되는 Refresh Token의 로그인 세션 식별자 |
| 저장소 | Object Storage | `object_storage` | - | 인프라, 파일 저장 설명 | OCI Object Storage 기준 |
| 기기 | 기기 | `device` | - | 테이블, 컬럼, API | 사용자의 주 사용 촬영 기기를 나타내는 표시용 태그. 공유 그룹 멤버별 표시에 사용 |

## 4. 공유 탭, 공유 그룹, 공유집(앨범)의 구분

| 한글 용어 | 의미 | 영어 사용 방식 |
|---|---|---|
| 공유 탭 | 여러 공유 그룹을 보여주는 하단 네비게이션 화면 | UI·문서에서 `share_tab` 사용. 테이블로 만들지 않음 |
| 공유 그룹 | 초대 코드와 참여자가 있는 최상위 공유 공간 | 개념·API·테이블에서 `shared_group` 사용 |
| 공유집(앨범) | 공유 그룹 안에서 사진을 담는 하위 공간 | 개념·API·테이블에서 `shared_album` 사용 |

`share_tab` 하나에 여러 `shared_group`이 표시된다.
`shared_group` 하나는 여러 `shared_album`과 여러 `photo`를 가질 수 있다.
사진은 `shared_group`에 직접 속하고, `shared_album_photo`를 통해 하나 이상의 `shared_album`에 속할 수 있다.
기술 문서에서는 하단 탭 화면을 `공유 탭`, 초대와 채팅 단위를 `공유 그룹`, 사진 업로드와 조회 단위를 `공유집(앨범)`으로 구분한다.

## 5. 사용자와 멤버의 구분

| 한글 용어 | 의미 | 영어 사용 방식 |
|---|---|---|
| 사용자 | Apple 로그인 후 서버에 등록된 사람 | 개념/API에서는 `user`, 테이블명은 `app_user` 사용 |
| 멤버 | 특정 공유 그룹에 초대받아 참여한 사용자의 역할 | 역할명으로 `member`, 역할 코드로 `MEMBER` 사용 |

단독 주체로는 `user`가 사용자를 의미한다.
특정 공유 그룹의 참여자를 표현할 때는 `member`를 사용하되 테이블이나 관계에서는 `shared_group_membership`처럼 공유 그룹 맥락을 포함한다.

## 6. 관계 표현 기준

```text
app_user
├── device
└── shared_group_membership
    ├── HOST
    └── MEMBER

shared_group
├── shared_group_membership
├── shared_group_chat_message
├── shared_album
│   └── shared_album_photo
└── photo
    ├── shared_album_photo
    ├── photo_like
    └── photo_comment

invite_code_reservation
└── shared_group
```

- `app_user`는 서버에 등록된 사용자이다.
- `device`는 사용자가 주로 촬영에 사용하는 기기를 나타내며 사용자 단위로 저장하는 표시용 태그이다.
- `shared_group`은 사용자들이 초대 코드로 참여하는 공유 그룹이다.
- `invite_code_reservation`은 공유 그룹이 존재하는 동안 발급한 초대 코드를 점유하고, 공유 그룹 물리 삭제 시 함께 삭제된다.
- `shared_group_membership`은 사용자가 특정 공유 그룹에 어떤 역할로 참여하는지를 표현한다.
- `HOST`는 공유 그룹을 만든 방장 역할이다.
- `MEMBER`는 공유 그룹에 초대받은 멤버 역할이다.
- `shared_group_chat_message`는 공유 그룹 안에서 작성된 일반 채팅 메시지이다.
- `shared_album`은 공유 그룹 안에서 공유집(앨범)을 표현하고 사진을 담는 단위이다.
- `photo`는 공유 그룹에 업로드된 사진 원본이다.
- `shared_album_photo`는 사진과 공유집(앨범)의 N:M 소속 관계를 표현한다.
- `shared_group.created_by_app_user_id`는 공유 그룹 최초 생성자 이력이다.
- `shared_album.created_by_app_user_id`는 공유집(앨범) 삭제 권한의 원칙적 기준이다. 생성자가 탈퇴한 경우 공유 그룹 방장이 삭제할 수 있다.
- `photo.uploaded_by_app_user_id`는 사진 원본 수정·삭제 권한의 원칙적 기준이다. 업로더가 탈퇴한 경우 공유 그룹 방장이 삭제할 수 있다.
- `photo_like`는 사용자가 특정 사진에 좋아요를 누른 상태이다.
- `photo_comment`는 단일 사진에 달리는 댓글이다.

## 7. 다음 단계 연결

3단계 데이터 표준화에서는 이 문서의 영어 표준명을 기준으로 아래 규칙을 확정한다.

- 테이블명 규칙
- 컬럼명 규칙
- 기본 키/외래 키 명명 규칙
- 날짜/시간 컬럼 규칙
- 코드값 규칙
- 삭제 정책 컬럼 규칙
- Object Storage 파일 참조 컬럼 규칙
