# Zipzip 데이터 표준화

## 1. 문서 목적

이 문서는 Zipzip 서버 데이터 모델링에 사용할 데이터 표준을 정의한다.
`01-domain-terminology.md`와 `02-korean-english-mapping.md`에서 확정한 용어를 기준으로 DBMS, 테이블명, 컬럼명, 타입, 날짜 컬럼, 코드값, 삭제 정책, 파일 참조 규칙을 정한다.

이 문서의 결정은 이후 ERD, SQL DDL, API 명세, 데이터 사전 작성의 기준으로 사용한다.

## 2. 의사결정 요약

| 항목 | 결정 | 이유 |
|---|---|---|
| DBMS | PostgreSQL | 프로젝트 기준 DBMS로 확정한다. `uuid`, `timestamptz`, partial index 등 현재 요구사항에 필요한 기능을 안정적으로 지원한다. |
| PK 타입 | `uuid` | API에서 식별자가 노출되어도 순차 ID 추측이 어렵고, 서버 애플리케이션에서 미리 생성할 수 있어 Object Storage 업로드 흐름과도 잘 맞는다. |
| PK 예외 | `invite_code_reservation.invite_code` | 초대 코드 예약 테이블은 주요 업무 엔티티가 아닌 점유 집합이므로 예약 대상 값 자체를 PK로 사용한다. |
| PK 생성 위치 | 애플리케이션 | DB 저장 전 식별자를 생성할 수 있어 파일 object key 구성, API 응답, 도메인 객체 생성 흐름이 단순해진다. |
| 테이블명 | 단수형 `snake_case` | 한글-영어 매핑 문서의 표준명과 1:1로 맞추기 쉽고, 엔티티 하나를 표현하는 이름으로 읽기 좋다. |
| 컬럼명 | `snake_case` | PostgreSQL과 SQL 관례에 맞고, Java/Kotlin/API 계층의 `camelCase`와 명확히 변환할 수 있다. |
| Java/Kotlin 필드명 | `camelCase` | JVM 생태계의 일반적인 컨벤션을 따른다. |
| API JSON 필드명 | `camelCase` | iOS/앱 클라이언트에서 다루기 쉽고, 서버 DTO 필드명과 맞추기 좋다. |
| 날짜/시간 컬럼 | `created_at`, `updated_at` | 모든 주요 업무 테이블의 기본 생성/수정 시각을 일관되게 추적한다. |
| Soft delete 컬럼 | `deleted_at` | soft delete 대상 테이블의 삭제 시각을 nullable 컬럼으로 관리한다. |
| 날짜/시간 타입 | `timestamptz` | 시간대 정보를 안전하게 다룰 수 있고, DB 저장 기준을 UTC로 통일하기 좋다. |
| Java 시간 타입 | `java.time.Instant` | PostgreSQL `timestamptz`와 UTC 기준 절대 시각을 그대로 매핑하고, 서버 기본 시간대에 따른 오해를 피한다. |
| 삭제 정책 | 데이터 성격별 혼합 정책 | 사용자와 공유 콘텐츠 엔티티는 soft delete한다. Refresh Token은 폐기 상태로 관리한다. 공유 그룹 멤버십과 사진 좋아요는 물리 삭제한다. 개별 삭제한 공유집(앨범)·사진과 삭제된 공유 그룹 데이터는 30일 뒤 물리 삭제한다. |
| Refresh Token 저장 | 원문 미저장, 해시 저장 | DB가 유출되어도 토큰 원문을 바로 사용할 수 없게 하며, 로그아웃/폐기 처리를 서버에서 제어할 수 있다. |
| 사진 파일 저장 | 이미지 파일은 Object Storage 저장 | 바이너리 파일을 DB에 직접 저장하지 않고, OCI Object Storage에 저장한다. |
| 사진 DB 정보 | `original_object_key`, `thumbnail_object_key`, `thumbnail_status`, `width`, `height` 저장 | Object Storage 조회, presigned URL 발급, 운영 확인에 필요한 최소 참조 정보이다. URL 자체는 저장하지 않고 조회 시점에 발급한다. |
| 사진-공유집(앨범) 관계 | `shared_album_photo` 관계 테이블 | 사진은 여러 공유집(앨범)에 중복 없이 속할 수 있어 N:M 관계 테이블로 표현한다. 사진은 공유 그룹에 직접 속하지 않고 이 매핑을 통해서만 공유집(앨범)에 속하며, 소속 공유 그룹이 필요하면 조인해 구한다. |
| 사진 그룹 기준 | iOS가 EXIF에서 추출해 전달한 nullable `taken_at` 저장 | 촬영일이 있으면 이를 사용하고, 없으면 `created_at`을 표시·정렬 기준으로 사용한다. EXIF 전체를 DB 컬럼으로 저장하지 않는다. |
| 사진 수 집계 | 실시간 count 우선 | 공유집(앨범)에 집계 컬럼을 두지 않고 `shared_album_photo`와 조인한 활성 사진 기준으로 count 쿼리를 수행한다. |
| 사진 좋아요 타입 | 단순 좋아요 | 현재 요구는 좋아요 여부와 수 표현이므로 이모지 반응 타입 없이 `photo_like` 존재 여부로 표현한다. |
| 그룹 채팅 저장 | `shared_group_chat_message` | 공유 그룹 안에서 사진 컨텍스트 없는 일반 채팅을 지원한다. 1차 전달 방식은 폴링이며 저장 모델은 전송 방식과 분리한다. |
| 기기 정보 저장 | 사용자 단위 기기명 저장 | 공유 그룹 관리 화면에서 방장/멤버별 주 사용 촬영 기기 태그를 표시해야 한다. 기기는 사용자에게 속한 표시용 정보로 관리하며 별도 유형은 두지 않는다. |
| 초대 코드 점유 | 예약 테이블 | `invite_code_reservation`으로 공유 그룹이 존재하는 동안 코드 중복 사용을 막고, 공유 그룹 물리 삭제 시 예약 행을 삭제해 점유를 해제한다. |
| 기기명 중복 기준 | `lower(btrim(name))` | 별도 정규화 컬럼을 두지 않고 PostgreSQL expression index로 원본 이름과 중복 판정 값의 불일치를 제거한다. |
| 텍스트 길이 | 댓글과 채팅 1,000자 | 무제한 `text`로 인한 과도한 입력을 막고 현재 UI의 일반 텍스트 요구를 수용한다. |
| 사용자 재가입 | 기존 `app_user` 복구 | 동일한 Apple 계정으로 재가입하면 기존 `app_user.deleted_at`을 해제하되 과거 공유 그룹 멤버십은 자동 복구하지 않는다. 탈퇴 시 `display_name`은 "탈퇴한 사용자"로 갱신한다. |
| 코드값 표기 | `UPPER_SNAKE_CASE` | 역할, 상태 등 코드값을 일반 문자열과 구분하기 쉽다. 예: `HOST`, `MEMBER` |

## 3. DBMS 표준

- DBMS는 PostgreSQL을 사용한다.
- SQL DDL은 PostgreSQL 문법을 기준으로 작성한다.
- UUID는 PostgreSQL native `uuid` 타입을 사용한다.
- 날짜/시간은 `timestamptz` 타입을 사용한다.
- JPA 엔티티의 날짜/시간 필드는 `java.time.Instant`를 사용한다.
- 문자열은 길이 제한이 명확한 경우 `varchar(n)`, 길이 제한을 두기 어려운 경우 `text`를 사용한다.
- JSON 형태의 동적 데이터가 꼭 필요한 경우에만 `jsonb`를 사용한다.

## 4. 식별자 표준

### 4.1 기본 키

- 모든 주요 테이블의 기본 키 컬럼명은 `id`로 통일한다.
- 기본 키 타입은 `uuid`를 사용한다.
- 기본 키 값은 애플리케이션에서 생성한다.
- 단, 초대 코드 예약 테이블인 `invite_code_reservation`은 `invite_code varchar(64)`를 PK로 사용하며 `id`, `updated_at`을 두지 않는다.

예시:

```sql
id uuid primary key
```

### 4.2 외래 키

- 외래 키 컬럼명은 `<참조_테이블명>_id` 형식을 사용한다.
- 같은 참조 테이블에 대한 업무 역할을 이름에 드러내야 하면 `<역할>_<참조_테이블명>_id` 형식을 사용한다.
- 참조 테이블명이 길더라도 의미가 줄어들지 않도록 축약하지 않는다.

예시:

```text
app_user.id       -> shared_group_membership.app_user_id
app_user.id       -> shared_group.created_by_app_user_id
app_user.id       -> shared_album.created_by_app_user_id
app_user.id       -> photo.uploaded_by_app_user_id
app_user.id       -> shared_group_chat_message.app_user_id
shared_group.id   -> shared_group_membership.shared_group_id
shared_group.id   -> shared_album.shared_group_id
shared_group.id   -> shared_group_chat_message.shared_group_id
shared_album.id   -> shared_album_photo.shared_album_id
photo.id          -> shared_album_photo.photo_id
device.id         -> photo.device_id
```

## 5. 명명 규칙

### 5.1 테이블명

- 테이블명은 단수형 `snake_case`를 사용한다.
- 한글-영어 매핑 문서의 영어 표준명을 우선한다.
- PostgreSQL 예약어 또는 ORM 충돌 가능성이 있는 이름은 피한다.

예시:

| 한글 | 테이블명 |
|---|---|
| 사용자 | `app_user` |
| 공유 그룹 | `shared_group` |
| 초대 코드 예약 원장 | `invite_code_reservation` |
| 공유 그룹 멤버십 | `shared_group_membership` |
| 공유집(앨범) | `shared_album` |
| 사진 | `photo` |
| 앨범-사진 매핑 | `shared_album_photo` |
| 그룹 채팅 메시지 | `shared_group_chat_message` |

### 5.2 컬럼명

- DB 컬럼명은 `snake_case`를 사용한다.
- Java/Kotlin 필드명과 API JSON 필드명은 `camelCase`를 사용한다.

예시:

| DB 컬럼명 | Java/Kotlin 필드명 | API JSON 필드명 |
|---|---|---|
| `shared_group_id` | `sharedGroupId` | `sharedGroupId` |
| `shared_album_id` | `sharedAlbumId` | `sharedAlbumId` |
| `invite_code` | `inviteCode` | `inviteCode` |
| `created_at` | `createdAt` | `createdAt` |

### 5.3 제약 조건과 인덱스명

| 대상 | 형식 | 예시 |
|---|---|---|
| Primary Key | `pk_<table>` | `pk_app_user` |
| Foreign Key | `fk_<table>__<referenced_table>` | `fk_photo__device` |
| Unique Key | `uk_<table>__<columns>` | `uk_shared_group__invite_code` |
| Index | `idx_<table>__<columns>` | `idx_photo__uploaded_by_app_user_id_deleted_at` |

## 6. 날짜/시간 컬럼 표준

### 6.1 기본 컬럼

모든 주요 업무 테이블에는 아래 컬럼을 둔다.

| 컬럼명 | 타입 | 필수 여부 | 설명 |
|---|---|---|---|
| `created_at` | `timestamptz` | 필수 | 생성 시각 |
| `updated_at` | `timestamptz` | 필수 | 마지막 수정 시각 |

예외:

- `invite_code_reservation`은 예약 행의 상태 변경을 기록하지 않으므로 `created_at`만 두고 `updated_at`을 두지 않는다.

### 6.2 Soft delete 컬럼

Soft delete 대상 테이블에는 아래 컬럼을 추가한다.

| 컬럼명 | 타입 | 필수 여부 | 설명 |
|---|---|---|---|
| `deleted_at` | `timestamptz` | 선택 | 삭제 처리 시각. `null`이면 활성 데이터 |

### 6.3 시간대 기준

- DB에는 UTC 기준으로 저장한다.
- API 응답도 UTC ISO-8601 형식을 기본으로 한다.
- 클라이언트 화면 표시 시간대는 앱에서 변환한다.

### 6.4 Java/JPA 매핑 기준

- DB의 `timestamptz` 컬럼은 JPA 엔티티에서 `java.time.Instant`로 매핑한다.
- `LocalDateTime`은 시간대 정보가 없는 지역 시각이므로 도메인 엔티티의 영속 시간 타입으로 사용하지 않는다.
- 서버가 생성·수정·삭제·만료·폐기 시각을 기록할 때는 UTC 기준 `Instant`를 저장한다.
- API JSON에서는 `Instant`를 UTC ISO-8601 문자열로 직렬화한다.
- 예: `2026-07-03T10:15:30Z`

## 7. 삭제 정책 표준

- `app_user`, `shared_group`, `device`, `shared_album`, `photo`는 soft delete를 사용한다.
- `app_user`는 Zipzip 서비스 탈퇴 시 `deleted_at`을 기록한다.
- `app_user`는 재가입 복구를 위해 물리 삭제하지 않고, 탈퇴 시 `display_name`을 "탈퇴한 사용자"로 갱신한다.
- `shared_group_membership`은 멤버 나가기 또는 Zipzip 서비스 탈퇴 시 물리 삭제한다.
- `refresh_token`은 `revoked_at`과 `expires_at`으로 생명주기를 관리하고 `deleted_at`을 사용하지 않는다.
- `photo_like`는 활성 공유 그룹 멤버이면서 해당 좋아요를 누른 사용자만 취소할 수 있다. 취소 또는 사용자 탈퇴 시 물리 삭제하고 `deleted_at`을 사용하지 않는다.
- `photo_comment`, `shared_group_chat_message`는 휴지통 없이 작성자 삭제 시 즉시 물리 삭제하고 `deleted_at`을 사용하지 않는다.
- soft delete 대상 데이터는 `deleted_at is not null`이면 삭제된 것으로 판단한다.
- soft delete 대상 조회 API는 기본적으로 `deleted_at is null`인 데이터만 반환한다.
- 활성 멤버십은 멤버십 행이 존재하고 상위 공유 그룹과 사용자가 모두 soft delete되지 않은 상태이다.
- 공유 그룹은 방장만 삭제할 수 있고 사용자에게 복구 기능을 제공하지 않는다.
- 사용자 탈퇴 후 기존 공유 콘텐츠의 작성자·생성자·업로더 표시는 "탈퇴한 사용자"로 대체한다.
- 사용자 탈퇴 시 해당 사용자의 활성 기기는 soft delete한다.
- 탈퇴한 생성자·업로더의 공유집(앨범)·사진 삭제는 해당 공유 그룹 방장이 할 수 있다.
- 공유 그룹 삭제 후 30일이 지나면 별도 배치가 Object Storage 객체와 DB 데이터를 물리 삭제한다.
- 개별 삭제한 공유집(앨범)과 사진도 각 `deleted_at`으로부터 30일이 지나면 같은 배치 정책으로 물리 삭제한다.
- 사진 원본 삭제는 `photo.deleted_at`으로 처리한다.
- 공유집(앨범) 삭제 시 해당 앨범-사진 매핑(`shared_album_photo`)은 즉시 물리 삭제한다. 다른 공유집(앨범)에도 속한 사진은 원본이 유지되지만, 그 공유집(앨범)이 마지막 소속이었던 사진은 원본도 함께 soft delete한다(사진은 공유 그룹에 직접 속하지 않고 공유집(앨범)을 통해서만 속하기 때문).
- Object Storage 파일 삭제는 DB soft delete와 분리해 비동기로 처리한다.
- Object Storage 객체 삭제가 필요한 사진은 객체 삭제가 성공했거나 삭제 재시도 작업을 기록한 뒤 DB 행을 물리 삭제한다.
- 객체 삭제가 실패하고 재시도 작업도 기록하지 못하면 DB 행은 남겨 재시도한다.
- Unique 제약이 soft delete 데이터와 충돌할 수 있는 경우 PostgreSQL partial unique index를 사용한다.
- `shared_group.invite_code`는 `invite_code_reservation.invite_code`를 참조하고 공유 그룹당 하나만 사용한다.
- 공유 그룹 물리 삭제 시 해당 `invite_code_reservation` 행도 삭제해 초대 코드 점유를 해제한다.
- DBML이 표현하지 못하는 partial/expression index는 `dbml/postgresql-overrides.sql`을 물리 마이그레이션에 함께 적용한다.

예시:

```sql
create unique index uk_shared_group_membership__group_user
    on shared_group_membership (shared_group_id, app_user_id);
```

## 8. 코드값 표준

- 코드값은 `UPPER_SNAKE_CASE`를 사용한다.
- DB에는 문자열로 저장한다.
- 코드값 컬럼명은 의미에 따라 `role`, `status`, `type` 등을 사용한다.

### 8.1 공유 그룹 역할 코드

| 한글 | 코드값 | 설명 |
|---|---|---|
| 방장 | `HOST` | 공유 그룹을 만든 사용자. 공유 그룹 삭제 권한을 가진다. |
| 멤버 | `MEMBER` | 초대 코드로 공유 그룹에 참여한 사용자. 공유 그룹 삭제 권한은 없다. |

## 9. 인증 토큰 저장 표준

### 9.1 사용자 탈퇴와 재가입

- 사용자 탈퇴 시 모든 활성 Refresh Token을 폐기하고 `app_user.deleted_at`을 기록한다.
- 사용자 탈퇴 시 `app_user.display_name`을 "탈퇴한 사용자"로 갱신하고, `app_user` 행은 재가입 복구를 위해 물리 삭제하지 않는다.
- 방장으로 만든 공유 그룹은 soft delete하고, `MEMBER`로 참여 중인 공유 그룹 멤버십은 물리 삭제한다.
- 탈퇴한 사용자가 기존에 생성·작성·업로드한 공유 콘텐츠는 즉시 삭제하지 않는다.
- 탈퇴한 사용자의 활성 기기는 soft delete하고, 사진 좋아요는 물리 삭제한다.
- Apple 로그인 식별자는 전체 `app_user`에서 unique로 관리한다.
- 동일한 Apple 계정으로 재가입하면 기존 `app_user` 행의 `deleted_at`을 해제한다.
- 재가입해도 과거 공유 그룹 멤버십은 자동으로 복구하지 않는다.

### 9.2 Access Token

- Access Token은 서버가 자체 발급한 JWT를 사용한다.
- Access Token 원문은 DB에 저장하지 않는다.
