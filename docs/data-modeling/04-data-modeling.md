# Zipzip 데이터 모델링

## 1. 문서 목적

이 문서는 Zipzip 서버의 실제 데이터 모델을 설계하기 위한 문서이다.
앞선 문서에서 확정한 용어와 데이터 표준을 기준으로 테이블, 컬럼, 관계, 키, 제약 조건, 삭제 정책을 단계적으로 정의한다.

참고 문서:

- `00-figma-midfi-wireframe-context.md`
- `01-domain-terminology.md`
- `02-korean-english-mapping.md`
- `03-data-standardization.md`
- `decisions/data-model-decision-log.md`
- `dbml/zipzip.dbml`

## 2. 현재 설계 전제

- DBMS는 PostgreSQL을 사용한다.
- 주요 테이블의 PK는 애플리케이션에서 생성한 `uuid`를 사용한다.
- 테이블명과 DB 컬럼명은 `snake_case`를 사용한다.
- Java/Kotlin 필드와 API JSON 필드는 `camelCase`를 사용한다.
- 삭제 정책은 데이터 성격에 따라 soft delete, 상태 변경, 물리 삭제를 구분한다.
- 모든 주요 업무 테이블은 `created_at`, `updated_at`을 가진다. 불변 원장과 상태 변경이 없는 포함 관계 테이블은 예외를 둔다.
- soft delete 대상 테이블은 `deleted_at`을 가진다.
- 하단 네비게이션의 공유 뷰는 DB 엔티티로 만들지 않는다.
- 공유 뷰는 현재 사용자가 참여 중인 여러 공유 폴더 카드로 구성한다.
- 공유 폴더 플로우에서만 서버에 사진을 업로드한다.
- 이미지 파일은 OCI Object Storage에 저장한다.
- DB에는 Object Storage 참조에 필요한 최소 파일 정보만 저장한다.
- 공유 폴더 관리 화면에서 방장/멤버별 기기 태그를 표시하기 위해 사용자 단위 기기명을 저장한다.
- 공유 폴더 채팅은 독립 메시지 도메인이 아니라 공유 폴더 안의 사진 댓글을 모아 보는 뷰로 구성한다.
- 날짜별 사진 그룹은 촬영일 기준으로 표시하고 촬영일이 없으면 생성 시각을 사용한다.
- 공유 폴더 사진 수는 활성 사진 기준, 앨범 사진 수는 활성 포함 관계와 활성 사진 기준으로 실시간 count한다.
- 한 사진은 여러 앨범에 포함될 수 있으므로 사진 원본과 앨범 포함 관계를 분리한다.
- 앨범 정보 수정은 활성 멤버가 할 수 있고, 앨범 삭제는 생성자만 할 수 있다.
- 사진 원본은 업로더만 수정·삭제할 수 있다.
- 사진 댓글은 작성자만 수정·삭제할 수 있다.
- 멤버 강제 퇴장 기능은 제공하지 않는다.
- 나간 멤버는 공유 폴더 초대 코드로 다시 참여할 수 있다.
- 공유 폴더는 방장만 삭제할 수 있고 사용자에게 복구 기능을 제공하지 않는다.
- 공유 폴더 이름 같은 공유 폴더 정보 수정은 방장만 할 수 있다.
- 개별 삭제한 사진·앨범과 삭제된 공유 폴더의 데이터는 30일 뒤 물리 삭제한다.
- 초대 코드 예약 원장은 공유 폴더를 물리 삭제한 뒤에도 영구 보존한다.
- 동일한 Apple 계정으로 재가입하면 기존 `app_user`를 복구한다.
- Zipzip 서비스 탈퇴 시 방장으로 만든 공유 폴더는 soft delete하고 `MEMBER`로 참여 중인 공유 폴더 멤버십은 물리 삭제한다. 삭제된 공유 폴더의 `HOST` 멤버십은 공유 폴더 물리 삭제 시 FK cascade로 함께 삭제한다.

## 3. 도메인 계층

```text
공유 뷰(UI, 테이블 없음)
└── 공유 폴더(shared_folder)
    ├── 공유 폴더 멤버십(shared_folder_membership)
    ├── 채팅 뷰(UI, 사진 댓글 모아보기)
    ├── 앨범(shared_album)
    ├── 사진(photo)
    │   ├── 사진 좋아요(photo_like)
    │   └── 사진 댓글(photo_comment)
    └── 앨범 사진 포함 관계(shared_album_photo)
```

첫 번째 Figma 화면의 카드 하나는 `shared_folder` 한 행에 해당한다.
공유 폴더에 들어간 두 번째 화면의 앨범 카드 하나는 `shared_album` 한 행에 해당한다.

## 4. 4-1. 테이블 후보 확정

### 4.1 테이블 후보 판단 기준

- 로그인 이후 서버와 상호작용하는 기능인가?
- 공유 폴더 플로우에서 서버 저장이 필요한 데이터인가?
- 여러 사용자가 함께 조회하거나 수정해야 하는 데이터인가?
- Object Storage 파일과 연결되어 서버 참조 정보가 필요한가?
- 화면에 사용자 간 상호작용이 보이고 서버 동기화가 필요한가?

로그인 전 로컬 기능, 개인 앨범, 단순 화면 이름, 디자인 컴포넌트는 테이블 후보에서 제외한다.

### 4.2 테이블 후보 요약

| 한글 | 테이블명 | 필요 여부 | 설명 |
|---|---|---|---|
| 사용자 | `app_user` | 필요 | Apple 로그인 후 서버에 등록된 사용자를 저장한다. |
| Refresh Token | `refresh_token` | 필요 | Refresh Token 해시, 계열, 만료와 폐기 상태를 저장한다. |
| 초대 코드 예약 | `invite_code_reservation` | 필요 | 과거 발급분을 포함한 초대 코드의 영구 비재사용을 보장한다. |
| 공유 폴더 | `shared_folder` | 필요 | 초대 코드로 참여하는 실제 공유 공간을 저장한다. |
| 공유 폴더 멤버십 | `shared_folder_membership` | 필요 | 사용자가 어떤 공유 폴더에 어떤 역할로 참여하는지 저장한다. |
| 기기 | `device` | 필요 | 사용자 단위 기기 태그를 저장하고 공유 폴더 멤버별 표시에 사용한다. |
| 앨범 | `shared_album` | 필요 | 공유 폴더 안에서 사진을 묶는 하위 단위를 저장한다. |
| 앨범 사진 포함 관계 | `shared_album_photo` | 필요 | 한 사진이 여러 앨범에 포함될 수 있는 관계를 저장한다. |
| 사진 | `photo` | 필요 | 공유 폴더에 업로드된 사진 원본의 Object Storage 참조 정보를 저장한다. |
| 사진 좋아요 | `photo_like` | 필요 | 사용자가 특정 사진에 좋아요를 누른 상태를 저장한다. |
| 사진 댓글 | `photo_comment` | 필요 | 단일 사진에 달리는 댓글을 저장하며 채팅 뷰의 원천 데이터로 사용한다. |

1차 모델은 11개 테이블이며 별도 `album`, `chat_room`, `chat_message` 테이블은 두지 않는다.
하단 네비게이션의 앨범 뷰는 로컬 중심 기능이고, 서버에 저장되는 공유 폴더 내부 앨범은 `shared_album`으로 모델링한다.

### 4.3 테이블별 책임

#### 4.3.1 `app_user`

Apple 로그인에 성공해 서버에 등록된 사용자를 저장한다.

주요 책임:

- 서버 기준 사용자와 Apple 로그인 식별자 보관
- 공유 폴더 생성·참여 주체
- 앨범 생성 주체
- 사진 업로드, 좋아요, 댓글 작성 주체
- 사진을 앨범에 추가한 주체
- 사용자 탈퇴 상태와 동일 Apple 계정 재가입 복구
- Zipzip 서비스 탈퇴 시 방장 공유 폴더와 `MEMBER` 멤버십 정리

#### 4.3.2 `refresh_token`

사용자에게 발급한 Refresh Token의 해시와 생명주기를 저장한다.

주요 책임:

- Refresh Token 원문 대신 해시 저장
- 로그인 세션별 토큰 계열 관리
- 토큰 만료와 폐기 처리
- Refresh Token 회전과 재사용 탐지

#### 4.3.3 `invite_code_reservation`

공유 폴더 생성 시 발급한 초대 코드를 영구 예약하는 원장을 저장한다.

주요 책임:

- 과거 발급분을 포함한 초대 코드 중복 방지
- 공유 폴더 물리 삭제 후에도 초대 코드 재사용 차단
- 초대 코드와 공유 폴더 생성의 트랜잭션 일관성 보장

예약 행은 물리 삭제하지 않는다. 이 테이블은 주요 업무 엔티티가 아닌 불변 원장이므로 UUID `id`와 `updated_at`을 두지 않고 `invite_code` 자체를 PK로 사용한다.

#### 4.3.4 `shared_folder`

공유 뷰에 카드로 표시되는 실제 공유 공간을 저장한다.

주요 책임:

- 공유 폴더 이름과 고정 초대 코드 저장
- 최초 생성자 정보 연결
- 현재 방장 권한을 `shared_folder_membership.role = 'HOST'`로 판별
- 공유 폴더 삭제 상태 관리
- 앨범, 사진 수, 채팅 뷰의 조회 범위 제공

초대 코드는 `invite_code_reservation`에서 영구 예약하고 `shared_folder.invite_code`가 이를 참조한다.
공유 폴더 이름 같은 공유 폴더 정보는 방장만 수정할 수 있다.
공유 폴더는 방장만 삭제할 수 있고 사용자에게 복구 기능을 제공하지 않는다.
삭제 후 30일이 지나면 Object Storage 객체와 DB 데이터를 비동기로 물리 삭제한다.

#### 4.3.5 `shared_folder_membership`

사용자와 공유 폴더 사이의 참여 관계를 저장한다.

주요 책임:

- 사용자가 참여 중인 공유 폴더 목록 조회
- 공유 폴더 참여 사용자 목록과 역할 관리
- 방장/멤버 권한 판별
- 멤버 나가기와 초대 코드를 통한 재참여 관리

역할 코드:

- `HOST`: 공유 폴더를 만든 방장
- `MEMBER`: 초대 코드로 참여한 멤버

멤버 강제 퇴장 기능은 제공하지 않는다.
멤버가 직접 나가면 멤버십을 물리 삭제하고 다시 참여하면 새로운 `MEMBER` 멤버십을 생성한다.
멤버십 행이 존재하고 상위 공유 폴더가 soft delete되지 않은 경우에만 활성 멤버십으로 판단한다.

#### 4.3.6 `device`

사용자 단위 표시용 기기 태그를 저장한다.

주요 책임:

- 사용자가 사용하는 기기명 저장
- 공유 폴더 관리 화면에서 방장/멤버별 기기 태그 표시
- 한 사용자의 여러 기기 태그 관리

기기는 공유 폴더 멤버십이나 사진에 직접 종속되지 않는다.
기기 태그는 온보딩에서 선택한 주 사용 촬영 기기의 표시용 이름이며 별도 유형은 두지 않는다.

#### 4.3.7 `shared_album`

공유 폴더 안에서 사진을 묶는 하위 단위를 저장한다.

주요 책임:

- 상위 공유 폴더 연결
- 앨범 이름과 생성자 저장
- 사진이 없는 앨범 표현
- 앨범 삭제 상태 관리

앨범은 방장과 멤버 모두 생성할 수 있다.
앨범 이름 같은 앨범 정보 수정은 활성 방장과 멤버 모두 할 수 있고, 앨범 삭제는 해당 앨범 생성자만 할 수 있다.

#### 4.3.8 `shared_album_photo`

사진이 특정 앨범에 포함된 관계를 저장한다.

주요 책임:

- 앨범과 사진의 N:M 관계 표현
- 같은 사진을 같은 앨범에 중복 추가하지 않도록 제어
- 사진을 앨범에 추가한 사용자 이력 저장
- 앨범에서만 제거할 때 관계 행 물리 삭제

`선택한 사진 공유앨범에 복사`는 새 사진 파일을 만들지 않고 `shared_album_photo` 행을 추가한다.
`이 앨범에서만 제거`는 `shared_album_photo` 행만 삭제하고 사진 원본은 유지한다.

#### 4.3.9 `photo`

공유 폴더 안에 업로드된 사진 원본의 Object Storage 참조 정보를 저장한다.

주요 책임:

- 사진이 속한 공유 폴더와 업로더 연결
- Object Storage object key 저장
- 원본 파일명, content type, 파일 크기 저장
- nullable 촬영일시와 날짜별 그룹 기준 제공
- 사진 삭제 상태 관리

서버는 이미지 EXIF에서 촬영일시만 추출해 `taken_at`에 저장하고 EXIF 전체를 DB 컬럼으로 저장하지 않는다.
이미지 파일의 EXIF는 제거하지 않고 업로드된 원본 파일을 Object Storage에 저장한다.
촬영일시가 없으면 `created_at`을 표시·정렬 기준으로 사용한다.
사진 원본 수정과 삭제는 해당 사진 업로더만 할 수 있다.
로컬 저장공간 또는 로컬 앨범에서 공유 폴더로 사진을 불러오는 플로우는 사진 업로드로 처리한다.
공유 사진 또는 공유 앨범을 로컬 저장공간으로 복사하는 플로우는 다운로드이며 서버 엔티티를 생성하지 않는다.

#### 4.3.10 `photo_like`

사용자가 특정 사진에 누른 단순 좋아요 상태를 저장한다.

- 한 사용자는 같은 사진에 한 번만 좋아요를 누를 수 있다.
- `photo_id`, `app_user_id` 조합에 unique 제약을 둔다.
- 좋아요 취소는 활성 멤버이면서 해당 좋아요를 누른 사용자만 할 수 있고, 취소 시 행을 물리 삭제한다.

#### 4.3.11 `photo_comment`

단일 사진에 달리는 댓글을 저장한다.

- 사진, 댓글 작성자, 댓글 내용을 저장한다.
- 공유 폴더 채팅 뷰는 이 댓글을 공유 폴더 범위로 모아 보여준다.
- 채팅 뷰에서 새 글을 작성하는 경우에도 특정 사진에 대한 댓글로 저장한다.
- 댓글 수정과 삭제는 해당 댓글 작성자만 할 수 있다.

#### 4.3.12 채팅 뷰(테이블 없음)

공유 폴더 채팅은 별도 테이블을 저장하지 않는 조회 뷰이다.

- `chat_room`, `chat_message`는 1차 ERD에 포함하지 않는다.
- 채팅 목록은 `photo_comment`를 `photo`, `shared_folder`와 조인해 조회한다.
- 목록 정렬은 댓글 `created_at`, `id` 기준의 안정적인 시간순을 사용한다.
- 사진 컨텍스트가 없는 독립 메시지는 1차 범위에서 저장하지 않는다.

## 5. 제외/보류 테이블

| 한글 | 제외/보류 테이블 후보 | 판단 | 이유 |
|---|---|---|---|
| 공유 뷰 | `share_view` | 제외 | 하단 네비게이션 UI이며 데이터 엔티티가 아니다. |
| 별도 앨범 | `album` | 제외 | 공유 폴더 내부 앨범은 로컬 앨범과 구분하기 위해 `shared_album`으로 모델링한다. |
| 개인 앨범 | `personal_album` | 제외 | 서버와 상호작용하지 않는 로컬 전용 개념이다. |
| 사진 메타데이터 | `photo_metadata` | 제외 | 서버가 이미지 EXIF에서 촬영일시만 추출해 nullable `photo.taken_at`에 저장하고 별도 메타데이터 테이블은 두지 않는다. |
| 채팅방 | `chat_room` | 제외 | 공유 폴더 채팅은 사진 댓글을 모아 보여주는 뷰이며 별도 채팅방 엔티티를 저장하지 않는다. |
| 채팅 메시지 | `chat_message` | 제외 | 채팅 뷰의 글은 특정 사진에 종속된 `photo_comment`로 저장한다. |
| 공유 폴더 초대 이력 | `shared_folder_invitation` | 보류 | 초대 이력·알림 정책이 확정되기 전에는 `shared_folder.invite_code`로 처리한다. |
| 알림 | `notification` | 보류 | 알림 화면과 서버 정책이 확정되지 않았다. |
| 앨범 좋아요 | `shared_album_like` | 보류 | 좋아요 UI는 사진 중심이며 앨범 좋아요 근거가 부족하다. |

## 6. 현재 기준 관계 초안

```text
app_user
├── refresh_token
├── shared_folder_membership
├── device
├── shared_album
├── shared_album_photo
├── photo
├── photo_like
└── photo_comment

invite_code_reservation
└── shared_folder

shared_folder
├── shared_folder_membership
├── shared_album
└── photo

shared_album
└── shared_album_photo

photo
├── shared_album_photo
├── photo_like
└── photo_comment
```

관계 해석:

- 한 사용자는 여러 공유 폴더에 참여할 수 있다.
- 하나의 초대 코드 예약은 최대 하나의 공유 폴더와 연결되며 공유 폴더 삭제 후에도 남는다.
- 한 공유 폴더는 여러 사용자를 멤버십으로 가질 수 있다.
- 한 공유 폴더는 여러 앨범을 가질 수 있다.
- 한 공유 폴더는 여러 사진 원본을 가질 수 있다.
- 한 앨범은 사진 없이 존재하거나 여러 사진을 포함할 수 있다.
- 한 사진은 여러 앨범에 포함될 수 있다.
- 공유 폴더 관리 화면의 기기 태그는 멤버십이 참조하는 사용자의 기기 목록으로 표시한다.
- 한 사용자는 여러 앨범을 생성하고, 여러 사진을 업로드하고, 여러 사진을 앨범에 추가할 수 있다.
- 한 사진은 여러 좋아요와 댓글을 가질 수 있다.
- 채팅 뷰는 한 공유 폴더 안의 여러 사진 댓글을 시간순으로 모아 보여준다.
- 공유 폴더 사진 수는 활성 사진 기준, 앨범 사진 수는 활성 앨범 포함 관계와 활성 사진 기준 실시간 count로 계산한다.
- 작성자·생성자·업로더·앨범 사진 추가자는 `app_user`로 보존하고 활성 공유 폴더 멤버십은 서비스 계층에서 검증한다.

## 7. 4-1 결정 사항

- 1차 ERD 대상은 `app_user`, `refresh_token`, `invite_code_reservation`, `shared_folder`, `shared_folder_membership`, `device`, `shared_album`, `shared_album_photo`, `photo`, `photo_like`, `photo_comment` 11개 테이블이다.
- 하단 네비게이션의 공유 뷰는 테이블로 만들지 않는다.
- 별도 `album` 테이블은 만들지 않는다.
- 초대 코드, 멤버십, 방장 역할, 관리, 채팅 뷰는 공유 폴더에 속한다.
- 앨범은 공유 폴더 아래에서 참여자들이 생성하는 사진 묶음이다.
- 사진 원본은 공유 폴더 아래에 속하고, 앨범과 사진의 포함 관계는 `shared_album_photo`가 표현한다.

## 8. 4-2 결정 사항

- 작성자·생성자·업로더·앨범 사진 추가자는 `app_user_id` 계열 컬럼으로 저장하고 공유 폴더 내부 쓰기 권한은 활성 멤버십으로 검증한다.
- 공유 폴더 정보 수정과 삭제는 방장만 할 수 있다.
- 앨범 정보 수정은 활성 멤버가 할 수 있고 앨범 삭제는 생성자만 할 수 있다.
- 사진 원본은 업로더만 수정·삭제할 수 있다.
- 앨범에서만 사진을 제거하면 `shared_album_photo` 관계만 삭제하고, 사진 원본 삭제는 `photo.deleted_at`으로 처리한다.
- 사진 댓글은 작성자만 수정·삭제할 수 있다.
- 멤버 강제 퇴장은 지원하지 않는다.
- 나간 멤버는 같은 공유 폴더의 유효한 초대 코드로 다시 참여할 수 있다.
- 공유 폴더는 방장만 삭제할 수 있고 사용자에게 복구 기능을 제공하지 않는다.
- 개별 삭제한 사진·앨범과 삭제된 공유 폴더의 데이터는 30일 뒤 물리 삭제한다.
- 초대 코드는 삭제된 공유 폴더를 포함해 의도적으로 재사용하지 않는다.
- 촬영일시가 없으면 `created_at`을 표시·정렬 기준으로 사용한다.
- Refresh Token은 다중 세션과 토큰 회전을 지원한다.
- 동일한 Apple 계정으로 재가입하면 기존 `app_user`를 복구하되 과거 공유 폴더 멤버십은 자동 복구하지 않는다.

세부 근거와 트랜잭션 규칙은 `decisions/data-model-decision-log.md`를 따른다.

## 9. 4-3. 테이블별 컬럼 설계

논리 모델과 일반 제약의 기준 파일은 `dbml/zipzip.dbml`이다.
DBML이 표현하지 못하는 PostgreSQL partial/expression index는 `dbml/postgresql-overrides.sql`에 정의한다. 물리 스키마에는 두 파일을 함께 적용해야 한다.
DBML의 모든 테이블과 컬럼에는 dbdocs에서 확인할 수 있는 한국어 설명을 작성한다.

| 테이블 | 주요 컬럼 |
|---|---|
| `app_user` | `apple_subject`, `display_name`, `deleted_at` |
| `refresh_token` | `token_hash`, `token_family_id`, `expires_at`, `revoked_at`, `replaced_by_refresh_token_id` |
| `invite_code_reservation` | `invite_code`, `created_at` |
| `shared_folder` | `created_by_app_user_id`, `name`, `invite_code`, `deleted_at` |
| `shared_folder_membership` | `shared_folder_id`, `app_user_id`, `role` |
| `device` | `app_user_id`, `name`, `deleted_at` |
| `shared_album` | `shared_folder_id`, `created_by_app_user_id`, `name`, `deleted_at` |
| `shared_album_photo` | `shared_album_id`, `photo_id`, `added_by_app_user_id`, `created_at` |
| `photo` | `shared_folder_id`, `uploaded_by_app_user_id`, 파일 참조 컬럼, `taken_at`, `deleted_at` |
| `photo_like` | `photo_id`, `app_user_id` |
| `photo_comment` | `photo_id`, `app_user_id`, `content`, `deleted_at` |

공통 규칙:

- 주요 업무 테이블의 PK는 애플리케이션에서 생성한 `uuid`이다.
- 불변 원장인 `invite_code_reservation`은 `invite_code` 자체를 PK로 사용하는 표준 예외다.
- 날짜/시간 컬럼은 `timestamptz`이며 DB 기본값은 `now()`를 사용한다.
- `updated_at`은 애플리케이션이 변경 시각을 갱신한다.
- `invite_code_reservation`과 `shared_album_photo`는 상태 변경을 기록하지 않으므로 `updated_at`을 두지 않는다.
- soft delete 대상에만 nullable `deleted_at`을 둔다.
- 촬영일을 알 수 없는 사진은 `taken_at`을 null로 유지한다.

## 10. 4-4. 관계와 키 설계

| 자식 테이블 | FK | 부모 테이블 | 관계 | 물리 삭제 정책 |
|---|---|---|---|---|
| `refresh_token` | `app_user_id` | `app_user` | N:1 | cascade |
| `shared_folder` | `invite_code` | `invite_code_reservation` | 1:1 | reservation delete restrict |
| `shared_folder` | `created_by_app_user_id` | `app_user` | N:1 | restrict |
| `shared_folder_membership` | `shared_folder_id` | `shared_folder` | N:1 | cascade |
| `shared_folder_membership` | `app_user_id` | `app_user` | N:1 | restrict |
| `device` | `app_user_id` | `app_user` | N:1 | cascade |
| `shared_album` | `shared_folder_id` | `shared_folder` | N:1 | cascade |
| `shared_album` | `created_by_app_user_id` | `app_user` | N:1 | restrict |
| `shared_album_photo` | `shared_album_id` | `shared_album` | N:1 | cascade |
| `shared_album_photo` | `photo_id` | `photo` | N:1 | cascade |
| `shared_album_photo` | `added_by_app_user_id` | `app_user` | N:1 | restrict |
| `photo` | `shared_folder_id` | `shared_folder` | N:1 | cascade |
| `photo` | `uploaded_by_app_user_id` | `app_user` | N:1 | restrict |
| `photo_like` | `photo_id`, `app_user_id` | `photo`, `app_user` | N:1 | cascade |
| `photo_comment` | `photo_id`, `app_user_id` | `photo`, `app_user` | N:1 | photo cascade, user restrict |

Soft delete에는 FK의 `ON DELETE`가 동작하지 않는다.
위 정책은 30일 뒤 물리 삭제 작업과 예기치 않은 직접 삭제를 제어하기 위한 FK 정책이다.

## 11. 4-5. 제약조건과 인덱스 설계

### 11.1 일반 unique

- `app_user.apple_subject`
- `refresh_token.token_hash`
- `refresh_token.replaced_by_refresh_token_id`
- `invite_code_reservation.invite_code` PK
- `shared_folder.invite_code`
- `shared_album_photo(shared_album_id, photo_id)`
- `photo.object_key`
- `photo_like(photo_id, app_user_id)`

### 11.2 일반 unique와 PostgreSQL partial unique

- 공유 폴더별 사용자 멤버십: `shared_folder_membership(shared_folder_id, app_user_id)`
- 공유 폴더별 방장: `shared_folder_membership(shared_folder_id) where role = 'HOST'`
- 활성 기기 태그: `device(app_user_id, lower(btrim(name))) where deleted_at is null`

DBML의 index setting은 partial/expression index를 완전하게 표현하지 못한다.
사용자 멤버십 일반 unique는 DBML에 정의하고, 방장과 활성 기기 태그 partial unique index는 `dbml/postgresql-overrides.sql`에 실행 가능한 SQL로 정의해 마이그레이션에 반드시 포함한다.

### 11.3 check 제약

- 사용자명, 공유 폴더명, 앨범명, 기기명, 댓글은 공백 문자열을 허용하지 않는다.
- 사진 댓글은 최대 1,000자로 제한하며 채팅 뷰에서도 같은 댓글 제한을 따른다.
- `photo.file_size`는 0보다 커야 한다.
- `photo.content_type`은 `image/`로 시작해야 한다.
- Refresh Token 만료·폐기 시각은 생성 시각보다 빠를 수 없다.

### 11.4 조회 인덱스

- 공유 폴더와 앨범 목록은 FK, `deleted_at`, `created_at` 순서의 복합 인덱스를 사용한다.
- 공유 폴더 사진 목록은 `coalesce(taken_at, created_at)`과 `id`를 사용해 안정적으로 정렬하고 `dbml/postgresql-overrides.sql`의 partial expression index를 사용한다.
- 앨범 사진 목록은 `shared_album_photo(shared_album_id, photo_id)`와 `photo`의 활성 표시 시각 인덱스를 조합해 조회한다.
- 사진 상세 댓글은 `photo_id`, `deleted_at`, `created_at` 인덱스를 사용한다.
- 채팅 뷰 댓글 목록은 `photo_comment(deleted_at, created_at, id)`와 `photo` 조인을 사용해 공유 폴더 범위로 조회한다.
- Refresh Token은 사용자·토큰 계열, `expires_at`, `revoked_at`의 개별 인덱스를 사용한다.
- 개별 삭제 데이터 정리 작업을 위해 `shared_album.deleted_at`, `photo.deleted_at` 인덱스를 사용한다.
- 사진 정리 기준 시각은 PostgreSQL `least(photo.deleted_at, shared_folder.deleted_at)`로 계산한다.

## 12. 4-6. dbdocs DBML ERD

- 기준 파일: `dbml/zipzip.dbml`
- PostgreSQL 보완 파일: `dbml/postgresql-overrides.sql`
- DBMS: PostgreSQL
- 테이블: 11개
- Enum: `shared_folder_role`
- 도메인 그룹: 인증, 공유 폴더, 사진
- dbdocs 프로젝트: `jhshin0422/zipzip`
- 게시 URL: `https://dbdocs.io/jhshin0422/zipzip`
- 접근 범위: Public

로컬 검증 명령:

```shell
dbdocs validate docs/data-modeling/dbml/zipzip.dbml
```

DBML 변경 후 재게시 명령:

```shell
dbdocs build docs/data-modeling/dbml/zipzip.dbml --project zipzip
```

## 13. 4-7. 검증 결과와 4단계 완료 기준

- 11개 테이블과 모든 참조 대상이 DBML에 존재한다.
- `invite_code_reservation`이 공유 폴더 물리 삭제 후에도 초대 코드의 영구 비재사용을 보장한다.
- 공유 폴더 채팅을 별도 테이블이 아닌 `photo_comment` 기반 조회 뷰로 정리했다.
- nullable FK와 `set null` 정책의 조합을 확인했다.
- soft delete 적용 테이블과 제외 테이블을 구분했다.
- 작성자·생성자·업로더·앨범 사진 추가자 FK와 권한 기준을 일치시켰다.
- 개별 삭제한 사진·앨범과 공유 폴더 삭제 데이터의 30일 물리 정리 정책을 일치시켰다.
- 사진 표시 시각 expression index와 두 partial unique index를 실행 가능한 PostgreSQL SQL로 분리했다.
- `dbdocs validate` 문법 검증을 통과했다.

DBML에 직접 표현하지 못한 PostgreSQL partial/expression index는 `dbml/postgresql-overrides.sql`에 정의했다.
이 기준으로 4단계 데이터 모델링은 완료하며 다음 단계는 데이터 사전 작성이다.
