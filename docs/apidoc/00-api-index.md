# Zipzip API 명세 인덱스

## 1. 문서 상태

- 상태: 데이터 모델 동기화 완료, 구현 전
- API 버전: `v1`
- Base Path: `/api/v1`
- 인증 방식: 자체 발급 Bearer JWT
- 공통 응답: `BaseResponse<T>`
- 서버 담당자: 미지정
- iOS 담당자: 미지정
- Notion 기준 양식: [api doc DB](https://app.notion.com/p/391b16d857ec80f2b0edf11d104b4aab)

현재 도메인 API는 구현 전이므로 Notion DB에 옮길 때 `상태=시작 전`, `구현 여부=false`, `연동 o/x=false`를 기본값으로 사용한다.

## 2. API index 결정

index는 화면 흐름이나 세부 행위가 아니라 리소스의 소유 도메인을 기준으로 분류한다. 기능이 늘어나도 기존 endpoint의 index가 쉽게 바뀌지 않도록 7개 카테고리로 고정한다.

`채팅`은 DB 엔티티 분류가 아니라 공유 폴더의 사진 댓글을 모아 보는 조회·실시간 전달 경계다.

| 순서 | index | 포함 범위 |
|---:|---|---|
| 1 | 인증 | Apple 로그인, 토큰 갱신, 로그아웃 |
| 2 | 사용자·기기 | 내 프로필, 사용자 탈퇴, 사용자 단위 기기 태그 |
| 3 | 공유 폴더 | 공유 폴더 CRUD, 멤버 조회, 초대 코드, 참여, 나가기 |
| 4 | 앨범 | 앨범 CRUD, 앨범 사진 포함 관계 추가·제거 |
| 5 | 사진 | 공유 폴더 사진 목록, 업로드, 촬영일시 수정, 단건·일괄 삭제 |
| 6 | 사진 반응·댓글 | 사진 상세, 좋아요, 사진 댓글 |
| 7 | 채팅 | 사진 댓글 기반 채팅 뷰, WebSocket 댓글 이벤트 구독 |

알림·푸시 모델이 확정되면 `알림` index를 별도로 추가한다. 현재 데이터 모델에 없는 기능을 기존 index 이름에 미리 포함하지 않는다.

## 3. 엔드포인트 목록

### 3.1 인증

| ID | Method | API Path | 이름 | 문서 |
|---|---|---|---|---|
| AUTH-01 | POST | `/api/v1/auth/apple` | Apple 로그인 | [02-authentication.md](02-authentication.md) |
| AUTH-02 | POST | `/api/v1/auth/refresh` | 토큰 갱신 | [02-authentication.md](02-authentication.md) |
| AUTH-03 | POST | `/api/v1/auth/logout` | 로그아웃 | [02-authentication.md](02-authentication.md) |

### 3.2 사용자·기기

| ID | Method | API Path | 이름 | 문서 |
|---|---|---|---|---|
| USER-01 | GET | `/api/v1/users/me` | 내 프로필 조회 | [03-profile-device.md](03-profile-device.md) |
| USER-02 | PATCH | `/api/v1/users/me` | 내 프로필 수정 | [03-profile-device.md](03-profile-device.md) |
| USER-03 | DELETE | `/api/v1/users/me` | 사용자 탈퇴 | [03-profile-device.md](03-profile-device.md) |
| DEVICE-01 | GET | `/api/v1/users/me/devices` | 내 기기 목록 조회 | [03-profile-device.md](03-profile-device.md) |
| DEVICE-02 | POST | `/api/v1/users/me/devices` | 내 기기 등록 | [03-profile-device.md](03-profile-device.md) |
| DEVICE-03 | PATCH | `/api/v1/users/me/devices/{deviceId}` | 내 기기 수정 | [03-profile-device.md](03-profile-device.md) |
| DEVICE-04 | DELETE | `/api/v1/users/me/devices/{deviceId}` | 내 기기 삭제 | [03-profile-device.md](03-profile-device.md) |

### 3.3 공유 폴더 목록·관리

| ID | Method | API Path | 이름 | 문서 |
|---|---|---|---|---|
| FOLDER-01 | GET | `/api/v1/shared-folders` | 내 공유 폴더 목록 조회 | [04-shared-folder.md](04-shared-folder.md) |
| FOLDER-02 | POST | `/api/v1/shared-folders` | 공유 폴더 생성 | [04-shared-folder.md](04-shared-folder.md) |
| FOLDER-03 | GET | `/api/v1/shared-folders/{sharedFolderId}` | 공유 폴더 상세 조회 | [04-shared-folder.md](04-shared-folder.md) |
| FOLDER-04 | PATCH | `/api/v1/shared-folders/{sharedFolderId}` | 공유 폴더 이름 수정 | [04-shared-folder.md](04-shared-folder.md) |
| FOLDER-05 | DELETE | `/api/v1/shared-folders/{sharedFolderId}` | 공유 폴더 삭제 | [04-shared-folder.md](04-shared-folder.md) |
| FOLDER-06 | GET | `/api/v1/shared-folders/{sharedFolderId}/members` | 공유 폴더 멤버 목록 조회 | [04-shared-folder.md](04-shared-folder.md) |

### 3.4 공유 폴더 초대·참여

| ID | Method | API Path | 이름 | 문서 |
|---|---|---|---|---|
| INVITE-01 | GET | `/api/v1/shared-folders/{sharedFolderId}/invite-code` | 초대 코드 조회 | [05-shared-folder-invitation.md](05-shared-folder-invitation.md) |
| INVITE-02 | POST | `/api/v1/shared-folders/join` | 초대 코드로 참여 | [05-shared-folder-invitation.md](05-shared-folder-invitation.md) |
| INVITE-03 | DELETE | `/api/v1/shared-folders/{sharedFolderId}/members/me` | 공유 폴더 나가기 | [05-shared-folder-invitation.md](05-shared-folder-invitation.md) |

### 3.5 앨범

| ID | Method | API Path | 이름 | 문서 |
|---|---|---|---|---|
| ALBUM-01 | GET | `/api/v1/shared-folders/{sharedFolderId}/shared-albums` | 앨범 목록 조회 | [06-shared-album.md](06-shared-album.md) |
| ALBUM-02 | POST | `/api/v1/shared-folders/{sharedFolderId}/shared-albums` | 앨범 생성 | [06-shared-album.md](06-shared-album.md) |
| ALBUM-03 | GET | `/api/v1/shared-albums/{sharedAlbumId}` | 앨범 상세 조회 | [06-shared-album.md](06-shared-album.md) |
| ALBUM-04 | PATCH | `/api/v1/shared-albums/{sharedAlbumId}` | 앨범 이름 수정 | [06-shared-album.md](06-shared-album.md) |
| ALBUM-05 | DELETE | `/api/v1/shared-albums/{sharedAlbumId}` | 앨범 삭제 | [06-shared-album.md](06-shared-album.md) |
| ALBUM-06 | PUT | `/api/v1/shared-albums/{sharedAlbumId}/photos/{photoId}` | 앨범에 사진 추가 | [06-shared-album.md](06-shared-album.md) |
| ALBUM-07 | DELETE | `/api/v1/shared-albums/{sharedAlbumId}/photos/{photoId}` | 앨범에서 사진 제거 | [06-shared-album.md](06-shared-album.md) |

### 3.6 사진

| ID | Method | API Path | 이름 | 문서 |
|---|---|---|---|---|
| PHOTO-01 | GET | `/api/v1/shared-folders/{sharedFolderId}/photos` | 공유 폴더 사진 목록 조회 | [07-photo-management.md](07-photo-management.md) |
| PHOTO-02 | POST | `/api/v1/shared-folders/{sharedFolderId}/photos` | 공유 폴더 사진 업로드 | [07-photo-management.md](07-photo-management.md) |
| PHOTO-03 | PATCH | `/api/v1/photos/{photoId}` | 사진 촬영일시 수정 | [07-photo-management.md](07-photo-management.md) |
| PHOTO-04 | DELETE | `/api/v1/photos/{photoId}` | 사진 삭제 | [07-photo-management.md](07-photo-management.md) |
| PHOTO-05 | POST | `/api/v1/shared-folders/{sharedFolderId}/photos/bulk-delete` | 사진 일괄 삭제 | [07-photo-management.md](07-photo-management.md) |

### 3.7 사진 반응·댓글

| ID | Method | API Path | 이름 | 문서 |
|---|---|---|---|---|
| REACTION-01 | GET | `/api/v1/photos/{photoId}` | 사진 상세 조회 | [08-photo-reaction.md](08-photo-reaction.md) |
| REACTION-02 | PUT | `/api/v1/photos/{photoId}/like` | 사진 좋아요 설정 | [08-photo-reaction.md](08-photo-reaction.md) |
| REACTION-03 | DELETE | `/api/v1/photos/{photoId}/like` | 사진 좋아요 취소 | [08-photo-reaction.md](08-photo-reaction.md) |
| COMMENT-01 | GET | `/api/v1/photos/{photoId}/comments` | 사진 댓글 목록 조회 | [08-photo-reaction.md](08-photo-reaction.md) |
| COMMENT-02 | POST | `/api/v1/photos/{photoId}/comments` | 사진 댓글 작성 | [08-photo-reaction.md](08-photo-reaction.md) |
| COMMENT-03 | PATCH | `/api/v1/photo-comments/{commentId}` | 사진 댓글 수정 | [08-photo-reaction.md](08-photo-reaction.md) |
| COMMENT-04 | DELETE | `/api/v1/photo-comments/{commentId}` | 사진 댓글 삭제 | [08-photo-reaction.md](08-photo-reaction.md) |

### 3.8 채팅

| ID | Method | API Path | 이름 | 문서 |
|---|---|---|---|---|
| CHAT-01 | GET | `/api/v1/shared-folders/{sharedFolderId}/chat-comments` | 채팅 뷰 댓글 목록 조회 | [09-chat-realtime.md](09-chat-realtime.md) |
| CHAT-WS-01 | WS | `/api/v1/ws` | 사진 댓글 이벤트 실시간 구독 | [09-chat-realtime.md](09-chat-realtime.md) |

## 4. Notion DB 등록 규칙

| Notion 속성 | 입력 규칙 |
|---|---|
| `이름` | 위 표의 API 이름을 입력한다. |
| `index` | 2장의 도메인 index를 입력한다. |
| `HTTP Method` | HTTP Method를 대문자로 입력한다. WebSocket은 `WS` 선택지를 추가한 뒤 입력한다. |
| `API Path` | REST와 WebSocket 모두 `/api/v1`부터 시작하는 전체 경로를 입력한다. |
| `상태` | 구현 전 `시작 전`, 구현 중 `진행 중`, 서버 완료 후 `완료`, 운영 반영 후 `배포 완료`를 사용한다. |
| `구현 여부` | 서버 구현과 테스트가 완료되면 체크한다. |
| `연동 o/x` | iOS 실기기 연동 검증이 완료되면 체크한다. |
| `서버 담당자` | 구현 이슈 배정 후 입력한다. |
| `ios 담당자` | 연동 이슈 배정 후 입력한다. |

Notion DB의 `index` 옵션은 2장의 7개 값으로 교체하고 `HTTP Method`에는 `WS`를 추가한다. 상세 근거는 [API 설계 의사결정 로그](decisions/api-design-decision-log.md)에 기록한다.
