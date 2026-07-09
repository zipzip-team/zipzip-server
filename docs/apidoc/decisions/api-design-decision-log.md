# Zipzip API 설계 의사결정 로그

## 1. 문서 목적

이 문서는 데이터 모델을 API 계약으로 변환하면서 확정한 분류, 멱등성, 권한 오류, 응답 DTO, 시간 표현, 이미지 URL, 그룹 채팅, Notion 문서화 규칙을 기록한다. API 명세와 구현이 충돌하면 이 문서의 결정을 기준으로 명세를 먼저 동기화한다.

- 결정 상태: 확정
- 최종 동기화일: 2026-07-08
- API 버전: `v1`
- 기준 문서: `docs/data-modeling/`, `docs/apidoc/00-api-index.md`, `docs/apidoc/01-common-spec.md`

## 2. 결정 요약

| ID | 주제 | 결정 |
|---|---|---|
| API-01 | Notion index | 리소스 소유 도메인 기준 7개 카테고리 사용 |
| API-02 | URI 버전 | REST API를 `/api/v1` 하위에 배치 |
| API-03 | 멱등성 | 중복 위험이 있는 POST API에 `Idempotency-Key` 필수 적용 |
| API-04 | 권한 오류 | 범위 밖 리소스는 404, 범위 안 소유권·역할 부족은 403 |
| API-05 | 응답 DTO | 현재 사용자 역할과 생성자·업로더·작성자 표현을 공통 구조로 통일 |
| API-06 | 이미지 URL | 원본·썸네일은 Object Storage 객체 키로만 저장하고, API는 조회 시점마다 만료 시각이 있는 presigned URL을 새로 발급 |
| API-07 | 실시간 전달 범위 | WebSocket 실시간 전달은 1차 범위에서 제외하고 후속 의사결정으로 분리 |
| API-08 | Notion 템플릿 | endpoint별 페이지와 공통 규격 참조를 조합해 중복을 제어 |
| API-09 | 사진·공유집(앨범) 경계 | 사진은 공유 그룹에 직접 속하지 않고 `shared_album_photo` N:M 관계로만 공유집(앨범)에 속하며, 전용 추가·제거 API로 관리. 사진은 항상 1개 이상의 공유집(앨범)에 속해야 한다 |
| API-10 | 그룹 채팅 | 그룹 채팅 메시지는 사진 댓글과 별도로 저장 |
| API-11 | 시간 표현 | 서버 `Instant`를 UTC ISO-8601 문자열로 직렬화해 요청·응답 계약에 사용 |
| API-12 | 업로드 발급-등록 무결성 | `photo_upload_reservation`으로 PHOTO-02 발급 대상과 PHOTO-03 등록 요청을 연결해 발급 대상 불일치와 재사용을 검증 |
| API-13 | 촬영 기기 정보 | 기기 CRUD API(DEVICE-01~04)를 폐지하고, 촬영 기기명은 PHOTO-03 요청의 `files[].deviceModel`로 받아 사진 응답의 `deviceModel` 문자열로 노출 |

## 3. API-01. Notion index 분류

### 결정

index는 화면 이름이나 세부 행위가 아니라 endpoint가 다루는 리소스의 소유 도메인으로 분류한다.

| index | 포함 리소스와 기능 |
|---|---|
| 인증 | Apple 로그인, Access/Refresh Token, 로그아웃 |
| 사용자 | 사용자 프로필, 사용자 탈퇴 |
| 공유 그룹 | 공유 그룹, 멤버십, 초대 코드, 참여와 나가기 |
| 앨범 | 공유집(앨범) CRUD |
| 사진 | 공유집(앨범) 사진 목록, 업로드 URL 발급·완료 등록, 단건·일괄 삭제, 앨범 추가·제거 |
| 사진 반응·댓글 | 사진 상세, 좋아요, 사진 댓글 |
| 채팅 | 그룹 채팅 메시지 목록, 작성, 수정, 삭제 |

Notion 기존 옵션은 다음과 같이 이관한다.

| 기존 index | 최종 index |
|---|---|
| 앱 시작·인증 | 인증 |
| 프로필·푸시 | 사용자 |
| 공유 그룹 목록·관리 | 공유 그룹 |
| 공유 그룹 초대·참여 | 공유 그룹 |
| 공유 사진집 | 앨범 |
| 사진 업로드·관리 | 사진 |
| 사진 상세·반응 | 사진 반응·댓글 |
| 채팅·실시간 | 채팅 |

### 판단 이유

- `목록·관리`, `업로드·관리` 같은 행위 이름은 기능 추가 때 경계가 자주 흔들린다.
- DB 엔티티와 같은 도메인 이름을 사용하면 서버 패키지, API 문서, 담당자 배정의 기준이 일치한다.
- 초대와 멤버십은 공유 그룹의 생명주기에 속하므로 별도 index로 분리하지 않는다.
- 실시간 전송은 채팅의 전달 방식이므로 `채팅·실시간` 대신 `채팅`으로 단순화한다.
- 알림 모델이 확정되면 기존 index에 섞지 않고 `알림`을 추가한다.

## 4. API-02. URI 버전과 Method

### 결정

- REST Base Path는 `/api/v1`이다.
- Notion `HTTP Method` select에는 `GET`, `POST`, `PATCH`, `DELETE`, `PUT`을 사용한다.

### 판단 이유

API 버전을 URI에 명시해 iOS와 서버의 계약 변경 범위를 분리한다. 1차 범위에는 WebSocket endpoint가 없으므로 REST Method만 Notion 선택지로 사용한다.

## 5. API-03. 상태 변경 POST API 멱등성

### 적용 대상

- AUTH-01, AUTH-02
- GROUP-02
- INVITE-02
- ALBUM-02
- PHOTO-03, PHOTO-06, PHOTO-07, PHOTO-08
- COMMENT-02
- CHAT-02

### 요청 계약

- 클라이언트는 요청 시도 묶음마다 UUID `Idempotency-Key`를 생성한다.
- 전송 실패나 응답 유실로 재시도할 때는 같은 key와 같은 body를 사용한다.
- 사용자가 새 작업을 시작하면 새 key를 사용한다.

### 서버 처리

1. 요청 주체, Method, Path, key로 멱등성 record를 조회한다.
2. 최초 요청이면 요청 hash와 `PROCESSING` 상태를 기록한 뒤 업무 transaction을 실행한다.
3. 완료된 HTTP status와 response body를 저장하고 `COMPLETED`로 변경한다.
4. 같은 hash의 재요청에는 저장한 응답을 반환하고 `Idempotency-Replayed: true`를 추가한다.
5. 다른 hash면 `409 IDEMPOTENCY_KEY_REUSED`, 처리 중이면 `409 IDEMPOTENCY_REQUEST_IN_PROGRESS`를 반환한다.

요청 본문은 정규화한 JSON으로 hash한다(사진 원본은 presigned URL로 직접 업로드하므로 API 요청 자체는 항상 JSON이다). Apple token, authorization code, Refresh Token 원문은 로그와 평문 record에 저장하지 않는다.

### 저장 결정

현재 Redis 의존성이 없으므로 PostgreSQL 기술 테이블 `api_idempotency_record`를 사용한다. 이 테이블은 업무 ERD의 도메인 엔티티 수에 포함하지 않지만 물리 마이그레이션에는 포함해야 한다.

필수 저장 항목은 scope, key, Method, Path, request hash, 상태, 암호화된 response, HTTP status, 만료일시다. scope·key·Method·Path 조합에는 unique 제약을 둔다. 일반 기록은 24시간, 인증 응답 기록은 암호화하여 10분 보관하고 만료 record는 배치로 삭제한다.

### 판단 이유

모바일 네트워크에서 성공 응답을 받지 못한 재시도는 정상 동작이다. DB unique 제약만으로 중복 행은 막을 수 있어도 최초 성공 응답을 복원할 수 없으며, Refresh Token 회전 재시도는 공격으로 오인될 수 있으므로 API 수준의 replay가 필요하다.

## 6. API-04. 인증·인가 오류 노출

### 결정

| 상황 | HTTP Status | 예시 |
|---|---:|---|
| 인증 정보 누락·만료·검증 실패 | 401 | `UNAUTHORIZED`, `ACCESS_TOKEN_EXPIRED` |
| 요청 사용자가 알 수 없는 공유 그룹 또는 다른 공유 그룹의 리소스 | 404 | `SHARED_GROUP_NOT_FOUND`, `PHOTO_NOT_FOUND` |
| 같은 공유 그룹의 활성 멤버지만 역할·소유권 부족 | 403 | `ONLY_HOST_CAN_UPDATE_SHARED_GROUP`, `NOT_PHOTO_UPLOADER` |

### 판단 이유

다른 공유 그룹 리소스의 존재 여부는 노출하지 않는다. 이미 접근 가능한 공유 그룹 안에서는 권한 문제를 클라이언트가 구분해 UI에 반영할 수 있도록 403 도메인 코드를 사용한다.

## 7. API-05. 응답 DTO 표준

### 결정

- 현재 요청 사용자의 공유 그룹 역할은 항상 `myRole`로 표현한다.
- 임의 사용자의 역할은 멤버 객체 안에서 `role`로 표현한다.
- 사용자 요약은 `{ "userId": UUID, "displayName": String }` 구조를 사용한다.
- 최초 생성자는 `createdBy`, 사진 업로더는 `uploadedBy`, 댓글 작성자는 `author`로 표현한다.
- 요청 사용자와 앨범 생성자가 같은지는 `isCreator`, 사진 업로더와 같으면 `isUploader`, 댓글 작성자와 같으면 `isAuthor`로 표현한다.
- DB 내부 FK 이름과 `deletedAt`은 클라이언트 응답에 직접 노출하지 않는다.

### 판단 이유

같은 의미를 scalar ID와 중첩 객체로 섞으면 iOS DTO와 화면 조합 로직이 불필요하게 분기된다. 관계의 의미를 필드명으로 드러내되 사용자 요약 구조는 재사용한다.

## 8. API-06. 이미지 URL

### 결정 (재개정)

- 사진 응답은 `originalUrl`/`thumbnailUrl`과 각각의 만료 시각(`originalUrlExpiresAt`/`thumbnailUrlExpiresAt`)을 함께 반환한다.
- `photo`는 URL이 아니라 Object Storage 객체 키(`originalObjectKey`, `thumbnailObjectKey`)만 저장하고, API는 조회 시점마다 presigned URL을 새로 발급한다.
- 원본 업로드도 presigned PUT URL로 처리한다. iOS가 [PHOTO-02](../07-photo-management.md#3-photo-02-사진-업로드-url-발급)로 발급받은 URL에 직접 업로드하고, 서버는 원본 바이트를 중계하지 않는다.
- 클라이언트는 URL을 영구 저장하지 않고 만료되면 사진 목록 또는 상세 API를 재조회한다.
- `thumbnailStatus`가 `PENDING`이거나 `FAILED`면 `thumbnailUrl`, `thumbnailUrlExpiresAt`은 `null`이다.

### 결정 이력과 변경 사유

1. 최초 결정: 사진 응답은 `imageUrl`과 `imageUrlExpiresAt`을 함께 반환하고 `objectKey`는 노출하지 않는다. 업로드는 `multipart/form-data`로 서버가 직접 받는다.
2. 1차 개정: `photo`에서 `object_key`를 제거하고 업로드 시점에 생성한 `original_url`, `thumbnail_url`을 만료 없이 직접 저장·반환하도록 바꿨다.
3. 2차 개정(현재): 배포 아키텍처 결정에 따라 제어 평면과 데이터 평면을 분리한다. 원본이 서버 인스턴스를 거치지 않고 iOS와 Object Storage 사이에서 직접 오가야 작은 인스턴스로도 대량 원본 업로드를 감당할 수 있기 때문이다. URL을 DB에 영구 저장하는 대신 객체 키만 저장하고 presigned URL을 매번 새로 발급하는 편이, 서명 정책 변경이나 접근 제어 강화에 더 유연하게 대응할 수 있다.

## 9. API-07. 실시간 전달 범위

### 결정

- WebSocket 실시간 전달은 1차 범위에서 제외하고 후속 의사결정으로 분리한다.
- 1차 채팅 API는 cursor 기반 폴링 조회를 사용한다.
- 실시간 전달을 도입할 때는 연결 인증, 재연결 보정, 이벤트 idempotency, 멤버십 상실 처리 정책을 별도 문서로 확정한다.

### 판단 이유

1차 구현은 폴링으로도 iOS 연동과 권한 검증을 단순하게 시작할 수 있다. 실시간 전달은 프로토콜·재연결·이벤트 보정 정책이 확정된 뒤 추가한다.

## 10. API-08. Markdown과 Notion 템플릿 운영

### 결정

- 로컬 Markdown은 도메인별 파일을 기준 문서로 사용한다.
- Notion DB는 endpoint 하나당 row와 하위 페이지 하나를 사용한다.
- Notion 페이지의 Request, Success Response, Fail Response는 해당 Markdown endpoint 절을 복사한다.
- 공통 Header, Common Response Format과 공통 오류 예시는 `01-common-spec.md`를 기준으로 Notion synced block 또는 링크로 삽입한다.
- endpoint별 Fail Response 표에는 그 API에서 추가되는 도메인 오류만 기록하고 401 등 공통 오류를 반복하지 않는다.
- 약식 응답 참조는 사용하지 않고 모든 Success Response를 완전한 JSON으로 작성한다.

### 판단 이유

공통 응답 전체를 40개 endpoint에 복제하면 변경 시 쉽게 어긋난다. Notion 템플릿의 정보 구조는 유지하되 공통 내용은 단일 기준으로 관리한다.

## 11. API-09. 사진과 공유집(앨범) 경계

### 결정 (재개정)

- 사진은 공유 그룹에 직접 속하지 않는다. `sharedGroupId`는 저장된 값이 아니라 사진의 `shared_album_photo` 매핑을 통해 조회 시점마다 계산해 응답에 포함하는 파생 필드다.
- 사진 목록 조회와 업로드 경로는 `/shared-albums/{sharedAlbumId}/photos`를 기준으로 하며, 업로드 완료 등록 시 대상 공유집(앨범)에 대한 `shared_album_photo` 매핑을 함께 만든다. 이 매핑이 사진의 유일한 소속이다.
- 기존 사진을 다른 공유집(앨범)에 추가·제거하는 전용 API(PHOTO-07, PHOTO-08)를 제공한다. 추가 시 대상 공유집(앨범)은 반드시 그 사진의 기존 공유집(앨범)과 같은 공유 그룹에 속해야 한다(`PHOTO_NOT_IN_SAME_SHARED_GROUP`).
- 앨범 경로가 없는 사진 상세(`GET /photos/{photoId}`)는 단일 `sharedAlbumId` 대신 소속된 모든 활성 공유집(앨범) 식별자 배열인 `sharedAlbumIds`를 반환한다.
- 사진은 항상 1개 이상의 공유집(앨범)에 속해야 한다. 공유집(앨범) 삭제(ALBUM-05)나 명시적 제거(PHOTO-08)로 사진의 마지막 매핑이 없어지면, 매핑 삭제와 함께 사진 원본도 soft delete한다.
- 사진 원본 삭제(PHOTO-05, PHOTO-06)는 소속된 모든 공유집(앨범)에서 접근을 차단하는 명시적 동작이며, 마지막 매핑 제거로 인한 위 캐스케이드 삭제와는 트리거가 다르지만 결과(soft delete)는 같다.
- 사진 업로드는 presigned URL 발급(PHOTO-02)과 완료 등록(PHOTO-03) 2단계로 나눈다. 원본 바이트는 서버를 거치지 않고 iOS와 Object Storage 사이에서 직접 오간다.

### 결정 이력과 변경 사유

1. 최초 결정: 업로드된 사진은 요청 경로의 공유집(앨범)에 직접 속하고, 기존 사진을 공유집(앨범)에 추가하거나 제거하는 별도 관계 API는 제공하지 않는다.
2. 1차 개정: 한 사진을 여러 공유집(앨범)에 중복 업로드 없이 담아야 하는 요구가 확인되어 N:M으로 개정하면서, 사진에 `sharedGroupId`를 직접 속성처럼 저장하고 응답에도 그대로 노출했다.
3. 2차 개정(현재): 1차 개정은 데이터 모델 결정(`docs/data-modeling/decisions/data-model-decision-log.md` DM-06)과 어긋났다 — 사진은 공유집(앨범)을 통해서만 그룹에 속해야 공유집(앨범) 삭제 시 사진 캐스케이드(다른 공유집(앨범)에 있으면 유지, 없으면 함께 삭제)가 성립한다. API 응답의 `sharedGroupId`는 유지하되, 저장된 속성이 아니라 매핑을 조인해 계산하는 파생 필드로 재정의했다. 사진 목록·업로드 API의 경로 형태는 유지해 클라이언트 화면 흐름과의 연속성을 지켰다.

## 12. API-10. 그룹 채팅

### 결정

- 사진 댓글은 `photo_comment`에 저장한다.
- 사진 컨텍스트 없는 그룹 채팅 메시지는 `shared_group_chat_message`에 저장한다.
- 1차 구현은 폴링 조회를 기준으로 한다.
- 메시지 작성, 조회, 수정, 삭제는 공유 그룹 활성 멤버십과 작성자 기준 권한을 검증한다.

### 판단 이유

사진 댓글과 그룹 채팅은 사용자 경험과 권한 기준이 다르다.
사진 댓글은 사진 상세에 종속되고, 그룹 채팅은 공유 그룹 전체 대화에 속하므로 저장 모델을 분리한다.

## 13. API-11. 시간 표현

### 결정

- API 시간 필드는 서버 내부 `java.time.Instant`를 기준으로 한다.
- 요청과 응답은 UTC ISO-8601 문자열을 사용한다.
- 예: `2026-07-03T10:15:30Z`
- DB의 `timestamptz` 컬럼과 API 시간 필드는 같은 절대 시각을 표현한다.
- `createdAt`, `updatedAt`, `deletedAt`, `expiresAt`, `revokedAt`, `takenAt`, `originalUrlExpiresAt`, `thumbnailUrlExpiresAt`, `uploadUrlExpiresAt`은 모두 이 규칙을 따른다.
- 서버 API 계약에서 `LocalDateTime`은 사용하지 않는다.

### 판단 이유

API 소비자는 여러 시간대의 기기일 수 있으므로 서버가 지역 시각을 의미하는 값을 내려주면 표시와 정렬 기준이 흔들릴 수 있다.
`Instant`와 UTC ISO-8601 문자열을 사용하면 DB 저장값, 서버 DTO, iOS 표시 변환의 경계가 명확해진다.

## 14. API-12. 업로드 발급-등록 무결성

### 결정

- PHOTO-02는 발급한 각 `objectKey`를 요청 사용자·대상 공유집(앨범)·만료 시각과 함께 `photo_upload_reservation`에 기록한다.
- PHOTO-03은 각 `objectKey`에 대응하는 `photo_upload_reservation`이 (1) 요청 사용자, (2) 요청 경로 공유집(앨범)과 일치하고, (3) 만료되지 않았는지 확인한 뒤에만 `photo` 행을 생성한다. 세 조건 중 하나라도 어긋나면 발급받은 적이 없는 것과 동일하게 `404 UPLOAD_OBJECT_NOT_FOUND`로 응답한다.
- 완료 등록에 성공하면 해당 예약 행을 같은 트랜잭션에서 물리 삭제해 재사용(같은 `objectKey`로 반복 등록)을 막는다.
- 만료된 미완료 예약과 대응하는 Object Storage 객체는 정기 스윕이 정리한다.

### 판단 이유

PHOTO-02가 발급 이력을 저장하지 않으면 PHOTO-03이 `objectKey`의 발급 대상(사용자·공유집(앨범))과 재사용 여부를 검증할 방법이 없다는 문제가 있었다. Object Storage HEAD 확인과 `photo.original_object_key` unique 제약만으로는 "이 사용자·이 공유집(앨범)에 발급된 key인가"를 확인할 수 없고, 다른 공유집(앨범)에서 발급받은 `objectKey`를 다른 경로로 등록하는 것도 막지 못한다.

대안으로 HMAC 기반 서명 completion token도 검토했다. 하지만 토큰만으로는 재사용 방지를 위해 결국 "사용됨" 상태를 어딘가에 저장해야 하므로 무상태라는 장점이 실질적이지 않고, 만료된 발급 건에 대응하는 Object Storage 고아 객체를 찾아 정리하는 스윕(`docs/architecture/backend-architecture.md` 4.1)도 지원할 수 없다. `invite_code_reservation`과 같은 예약 테이블 패턴을 그대로 재사용하는 편이 검증·재사용 방지·고아 객체 정리 세 가지 요구를 모두 충족하면서 기존 아키텍처(Redis 없이 PostgreSQL을 단일 진실 소스로 사용)와도 일관된다.

`photo.thumbnail_status`(`PENDING`/`READY`/`FAILED`)는 이 문제와 무관하다. `thumbnail_status`는 `photo` 행이 이미 생성된 뒤 비동기 썸네일 생성 진행 상태를 추적하는 필드이고, 이번 문제는 `photo` 행이 생성되기 전 PHOTO-03이 등록 요청 자체의 정당성을 검증하는 단계에서 발생한다.

## 15. API-13. 촬영 기기 정보

### 결정 (개정)

- 사용자가 기기를 직접 등록·수정·삭제하는 API(`DEVICE-01`~`DEVICE-04`, `/api/v1/users/me/devices`)를 폐지한다.
- 촬영 기기명은 PHOTO-03 완료 등록 요청의 `files[].deviceModel`로 iOS가 EXIF에서 추출한 값을 그대로 전달받는다.
- 사진 응답(PHOTO-01 목록, PHOTO-03 생성, REACTION-01 상세)은 `device` 중첩 객체 대신 `deviceModel` 문자열 필드로 노출한다. 값이 없으면 `null`이다.
- `사용자·기기` index는 `사용자`로 이름을 바꾸고 기기 관련 endpoint를 모두 제거한다.

### 결정 이력과 변경 사유

1. 최초 결정: 기기는 사용자가 직접 등록·관리하는 리소스로, `device` 엔티티와 전용 CRUD API로 표현했다. 공유 그룹 멤버 목록(GROUP-06)에도 사용자별 활성 기기 목록을 함께 노출했다.
2. 현재 개정: 기기명은 사용자가 직접 입력·관리할 필요 없이 사진마다 EXIF 메타데이터에서 그대로 얻을 수 있는 값으로 확인되어, 별도 엔티티·CRUD API·GROUP-06의 기기 목록 노출을 모두 제거하고 사진의 표시용 문자열 컬럼으로 단순화했다.

### 판단 이유

기기를 사용자 단위로 등록·중복 검증·soft delete하는 모델은, 실제로는 사진 한 장 한 장이 각자의 EXIF에서 촬영 기기명을 담고 있다는 사실과 맞지 않았다. 사진마다 다른 기기로 촬영될 수 있는데 사용자 단위 "주 사용 기기" 하나로는 이를 표현할 수 없고, 사용자가 직접 기기명을 등록·수정하게 하면 실제 촬영 기기와 어긋날 위험도 있다. 사진 메타데이터에서 그대로 받은 문자열을 `photo.device_model`에 저장하면 별도 엔티티의 생성·수정·삭제·중복 검증 API 없이도 같은 정보를 더 정확하게 표현할 수 있다.

## 16. 후속 구현 체크리스트

- [ ] PostgreSQL `api_idempotency_record` 마이그레이션과 정리 배치 구현
- [ ] 인증 응답 snapshot 암호화와 민감 정보 로그 마스킹 적용
- [ ] API DTO의 모든 시간 필드가 `Instant`로 직렬화되는지 테스트
- [ ] 공유 그룹 상세 응답의 최초 생성자와 현재 방장 구분 테스트
- [ ] 공유집(앨범) 삭제 후 앨범-사진 매핑 접근 차단과 30일 정리, 사진 원본 보존 테스트
- [ ] PHOTO-07/PHOTO-08 추가·제거 API의 멱등성과 공유 그룹 일치 검증 테스트
- [ ] PHOTO-02/PHOTO-03 presigned 업로드 흐름의 만료·재시도·부분 실패 시나리오 테스트
- [ ] `photo_upload_reservation` 발급 대상 불일치(다른 사용자·다른 공유집(앨범))와 재사용 시도에 대한 PHOTO-03 거부 테스트
- [ ] 만료된 `photo_upload_reservation`과 대응 Object Storage 고아 객체 정리 스윕 배치 구현
- [ ] 조회 시점마다 새로 발급하는 `originalUrl`/`thumbnailUrl`과 만료 시각 응답 계약 테스트
- [ ] 그룹 채팅 메시지 폴링 조회와 작성자 권한 통합 테스트
- [ ] WebSocket 실시간 전달을 도입할 경우 별도 API 의사결정 작성
