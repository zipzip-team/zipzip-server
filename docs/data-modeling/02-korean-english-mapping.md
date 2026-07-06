# Zipzip 한글-영어 용어 매핑

## 1. 문서 목적

이 문서는 `01-domain-terminology.md`에서 확정한 한글 업무 용어의 영어 표준명을 정의한다.
여기서 정한 영어명은 이후 데이터 표준화, API 명세, ERD, 테이블명, 컬럼명, 코드값 설계의 기준으로 사용한다.

## 2. 매핑 원칙

- 한글 표준 용어를 기준으로 영어명을 정한다.
- 영어명은 소문자 `snake_case`를 기본 표기법으로 사용한다.
- API 경로에서는 필요 시 복수형과 `kebab-case`를 사용할 수 있다.
- 역할 코드, 상태 코드 같은 코드값은 대문자 `UPPER_SNAKE_CASE`를 사용한다.
- 하단 네비게이션의 공유 뷰와 서버 엔티티인 공유 폴더를 구분한다.
- `사용자`와 `멤버`는 의미가 다르므로 서로 다른 영어명을 사용한다.

## 3. 핵심 용어 매핑

| 구분 | 한글 표준 용어 | 영어 표준명 | 코드/값 예시 | 사용 위치 | 비고 |
|---|---|---|---|---|---|
| 사람 | 사용자 | `user` | - | API, 문서, 개념 | Apple 로그인 후 서버에 등록된 사람 |
| 사람 | 사용자 테이블 | `app_user` | - | 테이블 | `user` 예약어/ORM 충돌 가능성을 피하기 위한 테이블명 |
| 화면 | 공유 뷰 | `share_view` | - | 화면, 문서 | 하단 네비게이션의 공유 탭. DB 엔티티로 사용하지 않음 |
| 화면 | 채팅 뷰 | `chat_view` | - | 화면, 문서, API 조회 모델 | 공유 폴더 안의 사진 댓글을 모아 보여주는 화면. DB 엔티티로 사용하지 않음 |
| 공유 공간 | 공유 폴더 | `shared_folder` | - | 테이블, 컬럼, API | 초대 코드, 멤버십, 관리, 앨범과 채팅 뷰의 기준이 되는 공유 공간 |
| 관계 | 공유 폴더 멤버십 | `shared_folder_membership` | - | 테이블, 관계, 권한 설명 | 사용자와 공유 폴더 사이의 참여 관계 |
| 사진 묶음 | 앨범 | `shared_album` | - | 테이블, 컬럼, API | 공유 폴더 하위에 존재 |
| 사진 묶음 관계 | 앨범 사진 포함 관계 | `shared_album_photo` | - | 테이블, 관계, API | 사진이 특정 앨범에 포함되어 있음을 표현 |
| 미디어 | 사진 | `photo` | - | 테이블, 컬럼, API | 공유 폴더 하위의 사진 원본 |
| 미디어 | 촬영일시 | `taken_at` | - | 컬럼, API | 서버가 이미지 EXIF에서 추출하는 nullable 값. 없으면 `created_at`을 표시·정렬 기준으로 사용 |
| 관계 | 공유 폴더·앨범 생성자 | `created_by_app_user_id` | - | 컬럼, API | 공유 폴더 최초 생성자 이력과 앨범 삭제 권한 기준 |
| 관계 | 사진 업로더 | `uploaded_by_app_user_id` | - | 컬럼, API | 사진 원본 수정·삭제 권한 기준 |
| 관계 | 앨범 사진 추가자 | `added_by_app_user_id` | - | 컬럼, API | 사진을 앨범에 포함시킨 사용자 이력 |
| 사진 반응 | 사진 좋아요 | `photo_like` | - | 테이블, 컬럼, API | 사용자가 특정 사진에 좋아요를 누른 상태 |
| 사진 댓글 | 사진 댓글 | `photo_comment` | - | 테이블, 컬럼, API | 단일 사진에 달리는 댓글이며 채팅 뷰의 원천 데이터 |
| 파일 | 이미지 파일 | `image_file` | - | Object Storage, 업로드 API | 실제 저장 대상 파일 |
| 역할 | 방장 | `host` | `HOST` | 역할 코드, 권한 검사 | 공유 폴더를 만든 사용자 |
| 역할 | 멤버 | `member` | `MEMBER` | 역할 코드, 권한 검사 | 공유 폴더에 초대받아 참여한 사용자 |
| 초대 | 초대 코드 | `invite_code` | - | 컬럼, API | 공유 폴더 참여 코드. 만료·재발급·재사용 없음 |
| 초대 | 초대 코드 예약 원장 | `invite_code_reservation` | - | 테이블 | 발급한 초대 코드를 영구 예약해 재사용을 차단하는 서버 내부 원장 |
| 로컬 개념 | 개인 앨범 | `personal_album` | - | 문서, 클라이언트 플로우 설명 | 서버 저장 대상 아님 |
| 인증 | Apple 로그인 | `apple_login` | - | API, 문서 | 로그인 성공 후 자체 JWT 발급 |
| 인증 | Access Token | `access_token` | - | API, 문서 | 서버 자체 발급 JWT |
| 인증 | Refresh Token | `refresh_token` | - | API, 문서 | 서버 자체 발급 JWT |
| 인증 | 토큰 계열 | `token_family_id` | - | 컬럼 | 회전되는 Refresh Token의 로그인 세션 식별자 |
| 저장소 | Object Storage | `object_storage` | - | 인프라, 파일 저장 설명 | OCI Object Storage 기준 |
| 기기 | 기기 | `device` | - | 테이블, 컬럼, API | 사용자의 주 사용 촬영 기기를 나타내는 표시용 태그. 공유 폴더 멤버별 표시에 사용 |

## 4. 공유 뷰와 공유 폴더의 구분

| 한글 용어 | 의미 | 영어 사용 방식 |
|---|---|---|
| 공유 뷰 | 여러 공유 폴더 카드를 보여주는 하단 네비게이션 화면 | UI·문서에서 `share_view` 사용. 테이블로 만들지 않음 |
| 공유 폴더 | 초대 코드와 참여자가 있는 실제 공유 공간 | 개념·API·테이블에서 `shared_folder` 사용 |

`share_view` 하나에 여러 `shared_folder`가 표시된다.
기술 문서에서는 하단 탭 화면을 `공유 뷰`, 실제 초대 공간을 `공유 폴더`로 구분한다.

## 5. 사용자와 멤버의 구분

| 한글 용어 | 의미 | 영어 사용 방식 |
|---|---|---|
| 사용자 | Apple 로그인 후 서버에 등록된 사람 | 개념/API에서는 `user`, 테이블명은 `app_user` 사용 |
| 멤버 | 특정 공유 폴더에 초대받아 참여한 사용자의 역할 | 역할명으로 `member`, 역할 코드로 `MEMBER` 사용 |

단독 주체로는 `user`가 사용자를 의미한다.
특정 공유 폴더의 참여자를 표현할 때는 `member`를 사용하되 테이블이나 관계에서는 `shared_folder_membership`처럼 공유 폴더 맥락을 포함한다.

## 6. 사용하지 않는 영어명

| 한글 용어 | 사용하지 않을 영어명 | 사용하지 않는 이유 | 대신 사용할 영어명 |
|---|---|---|---|
| 사용자 | `member`, `account` | 공유 폴더 역할인 멤버와 충돌하거나 계정 정보로 의미가 좁아질 수 있음 | `user`, `app_user` |
| 공유 뷰 | `shared_folder` | 서버 엔티티인 공유 폴더와 충돌함 | `share_view` |
| 채팅 뷰 | `chat_room`, `chat_message` | 별도 채팅방과 메시지 테이블을 만들지 않고 사진 댓글을 모아 보여주는 화면임 | `chat_view`, `photo_comment` |
| 공유 폴더 | `room`, `shared_room`, `party` | 제품 표준 용어와 다르고 채팅방으로 오해될 수 있음 | `shared_folder` |
| 앨범 | `album`, `folder` | 서버에 저장되는 공유 사진 묶음임을 명확히 표현하기 부족함 | `shared_album` |
| 앨범 사진 포함 관계 | `photo_album`, `album_photo` | 공유 폴더 안의 공유 앨범 맥락이 드러나지 않음 | `shared_album_photo` |
| 사진 | `image`, `picture` | 앱의 업무 용어가 사진임 | `photo` |
| 방장 | `owner`, `creator` | 권한보다 역할을 표현하는 용어가 필요함 | `host` |
| 초대 코드 | `join_code`, `room_code` | 공유 폴더 초대 목적을 직접 표현하기 부족함 | `invite_code` |

## 7. 관계 표현 기준

```text
app_user
├── device
└── shared_folder_membership
    ├── HOST
    └── MEMBER

shared_folder
├── chat_view(UI)
├── shared_album
├── photo
│   ├── photo_like
│   └── photo_comment
└── shared_album_photo

invite_code_reservation
└── shared_folder
```

- `app_user`는 서버에 등록된 사용자이다.
- `device`는 사용자가 주로 촬영에 사용하는 기기를 나타내며 사용자 단위로 저장하는 표시용 태그이다.
- `shared_folder`는 사용자들이 초대 코드로 참여하는 공유 폴더다.
- `invite_code_reservation`은 공유 폴더가 물리 삭제된 뒤에도 발급한 초대 코드를 영구 보존한다.
- `shared_folder_membership`은 사용자가 특정 공유 폴더에 어떤 역할로 참여하는지를 표현한다.
- `HOST`는 공유 폴더를 만든 방장 역할이다.
- `MEMBER`는 공유 폴더에 초대받은 멤버 역할이다.
- `shared_album`은 공유 폴더 안에서 사진을 묶는 단위이다.
- `photo`는 공유 폴더 안에 업로드된 사진 원본이다.
- `shared_album_photo`는 사진이 앨범에 포함된 관계이며, 같은 사진이 여러 앨범에 들어갈 수 있게 한다.
- `shared_folder.created_by_app_user_id`는 공유 폴더 최초 생성자 이력이다.
- `shared_album.created_by_app_user_id`는 앨범 삭제 권한의 기준이다.
- `photo.uploaded_by_app_user_id`는 사진 원본 수정·삭제 권한의 기준이다.
- `shared_album_photo.added_by_app_user_id`는 사진을 앨범에 추가한 사용자 이력이다.
- `photo_like`는 사용자가 특정 사진에 좋아요를 누른 상태이다.
- `photo_comment`는 단일 사진에 달리는 댓글이며 공유 폴더 채팅 뷰의 원천 데이터이다.
- `chat_view`는 공유 폴더 안의 `photo_comment`를 시간순으로 모아 보여주는 화면 또는 API 조회 모델이며 테이블로 만들지 않는다.

## 8. 다음 단계 연결

3단계 데이터 표준화에서는 이 문서의 영어 표준명을 기준으로 아래 규칙을 확정한다.

- 테이블명 규칙
- 컬럼명 규칙
- 기본 키/외래 키 명명 규칙
- 날짜/시간 컬럼 규칙
- 코드값 규칙
- 삭제 정책 컬럼 규칙
- Object Storage 파일 참조 컬럼 규칙
