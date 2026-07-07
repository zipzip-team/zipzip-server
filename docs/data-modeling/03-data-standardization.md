# Zipzip 데이터 표준화

## 1. 문서 목적

이 문서는 Zipzip 서버 데이터 모델링에 사용할 데이터 표준을 정의한다.
`01-domain-terminology.md`와 `02-korean-english-mapping.md`에서 확정한 용어를 기준으로 DBMS, 테이블명, 컬럼명, 타입, 날짜 컬럼, 코드값, 삭제 정책, 파일 참조 규칙을 정한다.

이 문서의 결정은 이후 ERD, SQL DDL, API 명세, 데이터 사전 작성의 기준으로 사용한다.

## 2. 의사결정 요약

| 항목 | 결정 | 이유 |
|---|---|---|
| DBMS | PostgreSQL | 프로젝트 기준 DBMS로 확정한다. `uuid`, `timestamptz`, partial index 등 현재 요구사항에 필요한 기능을 안정적으로 지원한다. |
| PK 타입 | `uuid` | API에서 식별자가 노출되어도 순차 ID 추측이 어렵고, 서버 애플리케이션에서 미리 생성할 수 있어 Object Storage 업로드 흐름과도 잘 맞는다. PostgreSQL이 native `uuid` 타입을 지원한다. |
| PK 예외 | `invite_code_reservation.invite_code` | 초대 코드 예약 원장은 주요 업무 엔티티가 아닌 불변 집합이므로 예약 대상 값 자체를 PK로 사용한다. |
| PK 생성 위치 | 애플리케이션 | DB 저장 전 식별자를 생성할 수 있어 파일 object key 구성, API 응답, 도메인 객체 생성 흐름이 단순해진다. |
| 테이블명 | 단수형 `snake_case` | 한글-영어 매핑 문서의 표준명과 1:1로 맞추기 쉽고, 엔티티 하나를 표현하는 이름으로 읽기 좋다. |
| 컬럼명 | `snake_case` | PostgreSQL과 SQL 관례에 맞고, Java/Kotlin/API 계층의 `camelCase`와 명확히 변환할 수 있다. |
| Java/Kotlin 필드명 | `camelCase` | JVM 생태계의 일반적인 컨벤션을 따른다. |
| API JSON 필드명 | `camelCase` | iOS/앱 클라이언트에서 다루기 쉽고, 서버 DTO 필드명과 맞추기 좋다. |
| 날짜/시간 컬럼 | `created_at`, `updated_at` | 모든 주요 업무 테이블의 기본 생성/수정 시각을 일관되게 추적한다. 상태 변경이 없는 관계 테이블은 예외를 둘 수 있다. |
| Soft delete 컬럼 | `deleted_at` | soft delete 대상 테이블의 삭제 시각을 nullable 컬럼으로 관리한다. |
| 날짜/시간 타입 | `timestamptz` | 시간대 정보를 안전하게 다룰 수 있고, DB 저장 기준을 UTC로 통일하기 좋다. |
| 삭제 정책 | 데이터 성격별 혼합 정책 | 주요 업무 엔티티는 soft delete하고, Refresh Token은 폐기 상태로 관리한다. 공유 폴더 멤버십, 사진 좋아요와 앨범 사진 포함 관계는 물리 삭제한다. 개별 삭제한 사진·앨범과 삭제된 공유 폴더 데이터는 30일 뒤 물리 삭제한다. |
| Refresh Token 저장 | 원문 미저장, 해시 저장 | DB가 유출되어도 토큰 원문을 바로 사용할 수 없게 하며, 로그아웃/폐기 처리를 서버에서 제어할 수 있다. |
| 사진 파일 저장 | 이미지 파일은 Object Storage 저장 | 바이너리 파일을 DB에 직접 저장하지 않고, OCI Object Storage에 저장한다. |
| 사진 DB 정보 | `object_key`, `original_file_name`, `content_type`, `file_size` 저장 | 파일 조회, 다운로드, 검증, 운영 확인에 필요한 최소 참조 정보이다. |
| 사진-앨범 관계 | `shared_album_photo` | 한 사진이 여러 앨범에 포함될 수 있으므로 사진 원본과 앨범 포함 관계를 분리한다. |
| 사진 그룹 기준 | 서버가 EXIF에서 추출한 nullable `taken_at` 저장 | 촬영일이 있으면 이를 사용하고, 없으면 `created_at`을 표시·정렬 기준으로 사용한다. EXIF 전체를 DB 컬럼으로 저장하지 않는다. |
| 사진 수 집계 | 실시간 count 우선 | 공유 폴더/앨범에 사진 수 집계 컬럼을 두지 않고, 공유 폴더는 활성 사진, 앨범은 활성 포함 관계와 활성 사진 기준으로 count 쿼리를 수행한다. 성능 문제가 확인되면 캐시 또는 집계 컬럼을 별도 검토한다. |
| 사진 좋아요 타입 | 단순 좋아요 | 현재 요구는 좋아요 여부와 수 표현이므로 이모지 반응 타입 없이 `photo_like` 존재 여부로 표현한다. |
| 기기 정보 저장 | 사용자 단위 기기명 저장 | 공유 폴더 관리 화면에서 방장/멤버별 주 사용 촬영 기기 태그를 표시해야 한다. 기기는 특정 공유 폴더 멤버십이 아니라 사용자에게 속한 표시용 정보로 관리하며 별도 유형은 두지 않는다. |
| 초대 코드 비재사용 | 영구 예약 원장 | `invite_code_reservation`을 삭제하지 않아 공유 폴더 물리 삭제 후에도 코드를 재사용하지 않는다. |
| 기기명 중복 기준 | `lower(btrim(name))` | 별도 정규화 컬럼을 두지 않고 PostgreSQL expression index로 원본 이름과 중복 판정 값의 불일치를 제거한다. |
| 텍스트 길이 | 사진 댓글 1,000자 | 무제한 `text`로 인한 과도한 입력을 막고 현재 UI의 일반 텍스트 요구를 충분히 수용한다. 채팅 뷰도 `photo_comment`를 사용하므로 같은 제한을 따른다. |
| 사용자 재가입 | 기존 `app_user` 복구 | 동일한 Apple 계정으로 재가입하면 새로운 사용자 행을 만들지 않고 기존 soft delete 행을 복구한다. |
| 코드값 표기 | `UPPER_SNAKE_CASE` | 역할, 상태 등 코드값을 일반 문자열과 구분하기 쉽다. 예: `HOST`, `MEMBER` |

## 3. DBMS 표준

- DBMS는 PostgreSQL을 사용한다.
- SQL DDL은 PostgreSQL 문법을 기준으로 작성한다.
- UUID는 PostgreSQL native `uuid` 타입을 사용한다.
- 날짜/시간은 `timestamptz` 타입을 사용한다.
- 문자열은 길이 제한이 명확한 경우 `varchar(n)`, 길이 제한을 두기 어려운 경우 `text`를 사용한다.
- JSON 형태의 동적 데이터가 꼭 필요한 경우에만 `jsonb`를 사용한다.

## 4. 식별자 표준

### 4.1 기본 키

- 모든 주요 테이블의 기본 키 컬럼명은 `id`로 통일한다.
- 기본 키 타입은 `uuid`를 사용한다.
- 기본 키 값은 애플리케이션에서 생성한다.
- 단, 불변 예약 원장인 `invite_code_reservation`은 `invite_code varchar(64)`를 PK로 사용하며 `id`, `updated_at`을 두지 않는다.

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
app_user.id              -> shared_folder_membership.app_user_id
app_user.id              -> shared_album.created_by_app_user_id
app_user.id              -> photo.uploaded_by_app_user_id
app_user.id              -> shared_album_photo.added_by_app_user_id
shared_folder.id           -> shared_album.shared_folder_id
shared_folder.id           -> photo.shared_folder_id
shared_album.id          -> shared_album_photo.shared_album_id
photo.id                 -> shared_album_photo.photo_id
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
| 공유 폴더 | `shared_folder` |
| 초대 코드 예약 원장 | `invite_code_reservation` |
| 공유 폴더 멤버십 | `shared_folder_membership` |
| 앨범 | `shared_album` |
| 앨범 사진 포함 관계 | `shared_album_photo` |
| 사진 | `photo` |

### 5.2 컬럼명

- DB 컬럼명은 `snake_case`를 사용한다.
- Java/Kotlin 필드명과 API JSON 필드명은 `camelCase`를 사용한다.

예시:

| DB 컬럼명 | Java/Kotlin 필드명 | API JSON 필드명 |
|---|---|---|
| `shared_folder_id` | `sharedFolderId` | `sharedFolderId` |
| `invite_code` | `inviteCode` | `inviteCode` |
| `created_at` | `createdAt` | `createdAt` |

### 5.3 제약 조건과 인덱스명

| 대상 | 형식 | 예시 |
|---|---|---|
| Primary Key | `pk_<table>` | `pk_app_user` |
| Foreign Key | `fk_<table>__<referenced_table>` | `fk_shared_album__shared_folder` |
| Unique Key | `uk_<table>__<columns>` | `uk_shared_folder__invite_code` |
| Index | `idx_<table>__<columns>` | `idx_shared_album__shared_folder_id` |

## 6. 날짜/시간 컬럼 표준

### 6.1 기본 컬럼

모든 주요 업무 테이블에는 아래 컬럼을 둔다.

| 컬럼명 | 타입 | 필수 여부 | 설명 |
|---|---|---|---|
| `created_at` | `timestamptz` | 필수 | 생성 시각 |
| `updated_at` | `timestamptz` | 필수 | 마지막 수정 시각 |

예외:

- `invite_code_reservation`은 불변 예약 원장이므로 `created_at`만 두고 `updated_at`을 두지 않는다.
- `shared_album_photo`는 앨범 포함 관계 생성만 기록하고 수정은 하지 않으므로 `created_at`만 두며, 앨범에서 제거할 때 행을 물리 삭제한다.

### 6.2 Soft delete 컬럼

Soft delete 대상 테이블에는 아래 컬럼을 추가한다.

| 컬럼명 | 타입 | 필수 여부 | 설명 |
|---|---|---|---|
| `deleted_at` | `timestamptz` | 선택 | 삭제 처리 시각. `null`이면 활성 데이터 |

### 6.3 시간대 기준

- DB에는 UTC 기준으로 저장한다.
- API 응답도 UTC ISO-8601 형식을 기본으로 한다.
- 클라이언트 화면 표시 시간대는 앱에서 변환한다.

예시:

```json
{
  "createdAt": "2026-07-03T10:15:30Z"
}
```

## 7. 삭제 정책 표준

- `app_user`, `shared_folder`, `device`, `shared_album`, `photo`, `photo_comment`는 soft delete를 사용한다.
- `shared_folder_membership`은 멤버 나가기 또는 Zipzip 서비스 탈퇴 시 물리 삭제한다.
- `refresh_token`은 `revoked_at`과 `expires_at`으로 생명주기를 관리하고 `deleted_at`을 사용하지 않는다.
- `photo_like`는 활성 멤버이면서 해당 좋아요를 누른 사용자만 취소할 수 있다. 취소 시 물리 삭제하고 `deleted_at`을 사용하지 않는다.
- `shared_album_photo`는 앨범에서만 사진을 제거할 때 물리 삭제하고 `deleted_at`을 사용하지 않는다.
- 채팅 뷰는 별도 테이블이 아니며 공유 폴더 안의 활성 `photo_comment`를 조회해 구성한다.
- soft delete 대상 데이터는 `deleted_at is not null`이면 삭제된 것으로 판단한다.
- soft delete 대상 조회 API는 기본적으로 `deleted_at is null`인 데이터만 반환한다.
- 공유 폴더는 방장만 삭제할 수 있고 사용자에게 복구 기능을 제공하지 않는다.
- 공유 폴더 삭제 후 30일이 지나면 별도 배치가 Object Storage 객체와 DB 데이터를 물리 삭제한다.
- 개별 삭제한 사진과 앨범도 각 `deleted_at`으로부터 30일이 지나면 같은 배치 정책으로 물리 삭제한다.
- 사진 원본 삭제는 `photo.deleted_at`으로 처리하고, 해당 사진의 모든 앨범 포함 관계는 조회에서 제외한다.
- 앨범에서만 사진을 제거하는 경우에는 `shared_album_photo` 행만 삭제하며 Object Storage 객체와 `photo` 행은 유지한다.
- Object Storage 파일 삭제는 DB soft delete와 분리해 비동기로 처리하며, 실패한 객체는 DB 행을 남겨 재시도한다.
- Unique 제약이 soft delete 데이터와 충돌할 수 있는 경우 PostgreSQL partial unique index를 사용한다.
- `shared_folder.invite_code`는 `invite_code_reservation.invite_code`를 참조하고 공유 폴더당 하나만 사용한다.
- `invite_code_reservation`은 물리 삭제하지 않으므로 공유 폴더 행을 정리한 뒤에도 DB 제약으로 과거 코드 재사용을 차단한다.
- DBML이 표현하지 못하는 partial/expression index는 `dbml/postgresql-overrides.sql`을 물리 마이그레이션에 함께 적용한다.

예시:

```sql
create unique index uk_shared_folder_membership__folder_user
    on shared_folder_membership (shared_folder_id, app_user_id);
```

## 8. 코드값 표준

- 코드값은 `UPPER_SNAKE_CASE`를 사용한다.
- DB에는 문자열로 저장한다.
- 코드값 컬럼명은 의미에 따라 `role`, `status`, `type` 등을 사용한다.

### 8.1 공유 폴더 역할 코드

| 한글 | 코드값 | 설명 |
|---|---|---|
| 방장 | `HOST` | 공유 폴더를 만든 사용자. 공유 폴더 삭제 권한을 가진다. |
| 멤버 | `MEMBER` | 초대 코드로 공유 폴더에 참여한 사용자. 공유 폴더 삭제 권한은 없다. |

## 9. 인증 토큰 저장 표준

### 9.1 사용자 탈퇴와 재가입

- 사용자 탈퇴 시 `app_user.deleted_at`을 기록하고 모든 활성 Refresh Token을 폐기한다.
- 탈퇴 사용자가 방장인 공유 폴더는 soft delete하고 `MEMBER`로 참여 중인 공유 폴더 멤버십은 물리 삭제한다.
- soft delete한 공유 폴더의 `HOST` 멤버십은 공유 폴더 물리 삭제 시 FK cascade로 함께 삭제한다.
- Apple 로그인 식별자는 탈퇴한 `app_user`에도 유지하며 전체 행에서 unique로 관리한다.
- 동일한 Apple 계정으로 재가입하면 새 행을 만들지 않고 기존 `app_user.deleted_at`을 해제한다.
- 사용자를 복구해도 과거 공유 폴더 멤버십은 자동으로 복구하지 않는다.

### 9.2 Access Token

- Access Token은 서버가 자체 발급한 JWT를 사용한다.
- Access Token 원문은 DB에 저장하지 않는다.

### 9.3 Refresh Token

- Refresh Token은 서버가 자체 발급한 JWT를 사용한다.
- Refresh Token 원문은 DB에 저장하지 않는다.
- DB에는 Refresh Token의 해시값만 저장한다.
- 여러 로그인 세션을 허용하며 로그인 세션마다 별도의 토큰 계열을 생성한다.
- Refresh Token 사용에 성공하면 기존 토큰을 폐기하고 같은 계열의 새 토큰을 발급한다.
- 이미 폐기된 토큰이 다시 제시되면 해당 토큰 계열 전체를 폐기한다.
- 로그아웃, 토큰 폐기, 세션 무효화를 위해 서버에서 해시값과 토큰 상태를 검증한다.

권장 컬럼 예시:

| 컬럼명 | 타입 | 설명 |
|---|---|---|
| `token_hash` | `varchar(255)` | Refresh Token 원문을 해시한 값. unique |
| `token_family_id` | `uuid` | 같은 로그인 세션에서 회전된 토큰 계열 식별자 |
| `expires_at` | `timestamptz` | 토큰 만료 시각 |
| `revoked_at` | `timestamptz` | 토큰 폐기 시각 |
| `replaced_by_refresh_token_id` | `uuid` | 회전 후 발급된 다음 Refresh Token 행 참조 |
| `created_at` | `timestamptz` | 토큰 생성 시각 |
| `updated_at` | `timestamptz` | 토큰 갱신 시각 |

## 10. Object Storage 파일 참조 표준

### 10.1 저장 범위

- 실제 이미지 파일은 OCI Object Storage에 저장한다.
- DB에는 파일 바이너리를 저장하지 않는다.
- DB에는 Object Storage에서 파일을 찾고 검증하는 데 필요한 최소 정보만 저장한다.
- EXIF 원본, 촬영 위치, 촬영 기기 같은 상세 사진 메타데이터는 DB 컬럼으로 저장하지 않는다.
- 서버는 업로드된 이미지 파일의 EXIF에서 촬영일시만 추출해 nullable `photo.taken_at`에 저장한다.
- 촬영일시를 추출한 뒤에도 이미지 파일의 EXIF를 제거하지 않고 업로드된 원본 파일을 Object Storage에 저장한다.
- 촬영일시가 없으면 `created_at`을 표시·정렬 기준으로 사용하며 `taken_at`에 대체값을 저장하지 않는다.
- 공유 폴더 멤버별 기기 태그 표시에 필요한 기기명은 사진 메타데이터가 아니라 사용자 단위 도메인 정보로 저장한다.

### 10.2 사진 테이블 파일 참조 컬럼

| 컬럼명 | 타입 | 필수 여부 | 설명 |
|---|---|---|---|
| `object_key` | `text` | 필수 | Object Storage 내 객체 키 |
| `original_file_name` | `varchar(255)` | 필수 | 업로드 당시 원본 파일명 |
| `content_type` | `varchar(100)` | 필수 | MIME type. 예: `image/jpeg` |
| `file_size` | `bigint` | 필수 | 파일 크기. byte 단위 |
| `taken_at` | `timestamptz` | 선택 | 서버가 이미지 EXIF에서 추출한 사진 촬영일시. 없으면 `created_at`을 표시·정렬 기준으로 사용 |

## 11. 다음 단계 연결

4단계 데이터 모델링에서는 이 문서의 표준을 기준으로 아래 내용을 설계한다.

- 테이블 목록
- 컬럼 목록
- PK/FK 관계
- Unique 제약
- Index
- Soft delete 적용 대상
- Object Storage 참조 구조
