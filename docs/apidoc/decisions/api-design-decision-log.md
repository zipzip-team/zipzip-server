# Zipzip API 설계 의사결정 로그

## 1. 문서 목적

이 문서는 데이터 모델을 API 계약으로 변환하면서 확정한 분류, 멱등성, 권한 오류, 응답 DTO, 이미지 URL, 그룹 채팅, Notion 문서화 규칙을 기록한다. API 명세와 구현이 충돌하면 이 문서의 결정을 기준으로 명세를 먼저 동기화한다.

- 결정 상태: 확정
- 최종 동기화일: 2026-07-07
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
| API-06 | 이미지 URL | 서명 URL과 만료 시각을 함께 반환하고 재조회로 갱신 |
| API-07 | 그룹 채팅 | `shared_group_chat_message` 기반 폴링 조회를 1차 범위로 적용 |
| API-08 | Notion 템플릿 | endpoint별 페이지와 공통 규격 참조를 조합해 중복을 제어 |
| API-09 | 사진·공유집(앨범) 경계 | 사진은 공유집(앨범)에 직접 속하고 사진 API는 `sharedAlbumId` 기준으로 설계 |
| API-10 | 그룹 채팅 | 그룹 채팅 메시지는 사진 댓글과 별도로 저장 |

## 3. API-01. Notion index 분류

### 결정

index는 화면 이름이나 세부 행위가 아니라 endpoint가 다루는 리소스의 소유 도메인으로 분류한다.

| index | 포함 리소스와 기능 |
|---|---|
| 인증 | Apple 로그인, Access/Refresh Token, 로그아웃 |
| 사용자·기기 | 사용자 프로필, 사용자 탈퇴, 사용자 단위 기기 태그 |
| 공유 그룹 | 공유 그룹, 멤버십, 초대 코드, 참여와 나가기 |
| 앨범 | 공유집(앨범) CRUD |
| 사진 | 공유집(앨범) 사진 목록, 업로드, 촬영일시, 삭제 |
| 사진 반응·댓글 | 사진 상세, 좋아요, 사진 댓글 |
| 채팅 | 그룹 채팅 메시지 목록, 작성, 수정, 삭제 |

Notion 기존 옵션은 다음과 같이 이관한다.

| 기존 index | 최종 index |
|---|---|
| 앱 시작·인증 | 인증 |
| 프로필·푸시 | 사용자·기기 |
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
- DEVICE-02
- GROUP-02
- INVITE-02
- ALBUM-02
- PHOTO-02, PHOTO-05
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

JSON은 정규화한 body로 hash하고 multipart는 각 파일의 content digest와 metadata를 함께 hash한다. Apple token, authorization code, Refresh Token 원문은 로그와 평문 record에 저장하지 않는다.

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
- DB 내부 FK 이름과 `objectKey`, `deletedAt`은 클라이언트 응답에 직접 노출하지 않는다.

### 판단 이유

같은 의미를 scalar ID와 중첩 객체로 섞으면 iOS DTO와 화면 조합 로직이 불필요하게 분기된다. 관계의 의미를 필드명으로 드러내되 사용자 요약 구조는 재사용한다.

## 8. API-06. 이미지 서명 URL

### 결정

- 사진 응답은 `imageUrl`과 `imageUrlExpiresAt`을 함께 반환한다.
- Object Storage의 `objectKey`는 외부에 노출하지 않는다.
- 클라이언트는 URL을 영구 저장하지 않고 만료되면 사진 목록 또는 상세 API를 재조회한다.
- URL 유효 시간은 운영 설정이며 절대 시각인 `imageUrlExpiresAt`만 계약으로 보장한다.

### 판단 이유

만료 시각이 없으면 클라이언트가 캐시 실패와 네트워크 오류를 구분하기 어렵다. 별도 URL 갱신 endpoint보다 기존 조회 API를 재사용하는 편이 1차 범위에 단순하다.

## 9. API-07. 그룹 채팅 조회 방식

### 결정

- 그룹 채팅 메시지는 `shared_group_chat_message`에 저장한다.
- 1차 API는 `/shared-groups/{sharedGroupId}/chat-messages`의 cursor 기반 폴링 조회와 작성 API를 제공한다.
- 메시지 수정·삭제는 `/shared-group-chat-messages/{messageId}`에서 작성자 권한으로 처리한다.
- WebSocket 실시간 전달은 1차 범위에서 제외하고 후속 의사결정으로 분리한다.

### 판단 이유

최신 데이터 모델은 사진 댓글 모아보기가 아니라 별도 그룹 채팅 메시지를 채택했다. 1차 구현은 폴링으로도 iOS 연동과 권한 검증을 단순하게 시작할 수 있고, 실시간 전달은 프로토콜·재연결·이벤트 보정 정책이 확정된 뒤 추가한다.

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

### 결정

- 사진 목록과 업로드 경로는 `/shared-albums/{sharedAlbumId}/photos`를 기준으로 한다.
- 업로드된 사진은 요청 경로의 공유집(앨범)에 직접 속한다.
- 기존 사진을 공유집(앨범)에 추가하거나 제거하는 별도 관계 API는 제공하지 않는다.
- 사진 상세는 `sharedAlbumId`를 반환한다.
- 공유집(앨범) 삭제는 하위 사진 접근을 함께 차단하고, 30일 뒤 Object Storage 객체와 DB 행을 정리한다.

### 판단 이유

제품 계층은 공유 탭, 공유 그룹, 공유집(앨범), 사진 순서다.
공유집(앨범)에 속하지 않는 사진이 없으므로 `photo.shared_album_id`로 직접 소속을 표현하는 편이 API 계약과 데이터 모델 모두 단순하다.

## 12. API-10. 그룹 채팅

### 결정

- 사진 댓글은 `photo_comment`에 저장한다.
- 사진 컨텍스트 없는 그룹 채팅 메시지는 `shared_group_chat_message`에 저장한다.
- 1차 구현은 폴링 조회를 기준으로 한다.
- 메시지 작성, 조회, 수정, 삭제는 공유 그룹 활성 멤버십과 작성자 기준 권한을 검증한다.

### 판단 이유

사진 댓글과 그룹 채팅은 사용자 경험과 권한 기준이 다르다.
사진 댓글은 사진 상세에 종속되고, 그룹 채팅은 공유 그룹 전체 대화에 속하므로 저장 모델을 분리한다.

## 13. 후속 구현 체크리스트

- [ ] PostgreSQL `api_idempotency_record` 마이그레이션과 정리 배치 구현
- [ ] 인증 응답 snapshot 암호화와 민감 정보 로그 마스킹 적용
- [ ] 공유 그룹 상세 응답의 최초 생성자와 현재 방장 구분 테스트
- [ ] 공유집(앨범) 삭제 후 하위 사진 접근 차단과 30일 정리 테스트
- [ ] 이미지 서명 URL 만료 시각 계약 테스트
- [ ] 그룹 채팅 메시지 폴링 조회와 작성자 권한 통합 테스트
- [ ] WebSocket 실시간 전달을 도입할 경우 별도 API 의사결정 작성
