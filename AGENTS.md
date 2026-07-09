# AGENTS.md

## 기본 규칙

- 모든 문서는 한국어로 작성할 것.
- 사용자가 명시적으로 요청하지 않은 파일은 임의로 수정하지 말 것.
- 특히 `README.md`처럼 프로젝트 첫 화면이나 문서 인덱스 역할을 하는 파일에는 사용자의 명시적인 요청 없이 링크, 안내 문구, 섹션을 추가하지 말 것.

## Gradle 실행 규칙

- Gradle 명령은 JDK 21로 실행할 것.
- 현재 로컬 기본 JDK가 21이 아닐 수 있으므로, 필요하면 `JAVA_HOME`을 JDK 21 경로로 지정해서 실행할 것.

## 테스트 전략 규칙

- 엔티티 골격, JPA 어노테이션, 생성 메서드, soft delete 필드 같은 도메인 기본 구조는 우선 순수 단위 테스트로 검증할 것.
- 개발용 PostgreSQL 컨테이너를 그대로 테스트에 사용하지 말 것.
- PostgreSQL 연동 테스트는 Flyway 마이그레이션, Repository, Service 권한 정책이 생기는 시점부터 도입할 것.
- PostgreSQL 연동 테스트가 필요하면 개발 DB가 아니라 테스트 전용 DB 또는 Testcontainers를 사용할 것.
- PostgreSQL 테스트는 매 실행마다 깨끗한 스키마에 `flyway migrate`가 적용되는 상태를 보장할 것.
- PostgreSQL 전용 기능인 partial index, expression index, check constraint, FK, unique 제약은 H2 대신 PostgreSQL 기반 테스트에서 검증할 것.
- Repository 테스트는 `@DataJpaTest` 또는 필요한 최소 Spring context를 사용하고, Service 권한 정책은 `@SpringBootTest` 또는 통합 테스트로 검증할 것.

## Swagger/OpenAPI 문서화 규칙

- iOS 클라이언트가 Swagger 문서를 API 명세로 사용하므로, 새 컨트롤러와 엔드포인트에는 Swagger 어노테이션을 반드시 작성할 것.
- 컨트롤러에는 `@Tag`, 각 API 메서드에는 `@Operation`과 주요 성공/실패 `@ApiResponse`를 작성할 것.
- 필수 헤더, 인증 헤더, 멱등성 헤더는 `@Parameter`로 필수 여부와 형식 예시를 명시할 것.
- 요청/응답 DTO의 public contract 필드에는 `@Schema`로 설명, 예시, 필수 여부 또는 제약을 명시할 것.
- Swagger 설명은 `docs/apidoc/`의 API 명세와 충돌하지 않게 작성하고, 오류 코드는 실제 `ErrorCode`의 문자열과 동일하게 표기할 것.

## Flyway 마이그레이션 규칙

- 엔티티 변경으로 테이블, 컬럼, 인덱스, 제약조건 등 DB 스키마가 변경되면 새 Flyway 마이그레이션 스크립트를 추가할 것.
- Java 코드에서만 의미가 있는 메서드 추가, 비즈니스 로직 변경, `@Transient` 필드 추가처럼 DB 스키마가 바뀌지 않는 변경에는 마이그레이션 스크립트를 추가하지 말 것.
- 이미 공유 환경에 적용된 마이그레이션 스크립트는 수정하지 말 것.
- 적용된 마이그레이션의 수정이 필요하면 기존 파일을 변경하지 말고 새 마이그레이션 스크립트를 추가할 것.
- 병렬 작업 시 버전 충돌을 줄이기 위해 순번 방식(`V2__...`) 대신 timestamp 방식(`VyyyyMMddHHmmss__description.sql`)을 사용할 것.
- 마이그레이션 파일명은 예를 들어 `V20260708153000__add_photo_deleted_at.sql`처럼 작성할 것.

## GitHub CLI 실행 규칙

- GitHub 인증 토큰이 필요한 `gh` 작업은 샌드박스 외부에서 실행할 것.

## 패키지 구조 규칙

- `domain` 패키지는 도메인 모듈의 최상위 경계로 사용할 것.
- JPA 엔티티 클래스는 도메인 모듈 하위의 `entity` 패키지에 둘 것.
- 엔티티 클래스를 `domain/<module>/domain` 패키지에 두지 말 것.
- 예시: `org.zipzip.zipzipserver.domain.user.entity.AppUser`

## Git Convention

- 🎉 **Start:** Start New Project `:tada:`
- ✨ **Feat:** 새로운 기능을 추가 `:sparkles:`
- 🐛 **Fix:** 버그 수정 `:bug:`
- 🎨 **Design:** CSS 등 사용자 UI 디자인 변경 `:art:`
- ♻️ **Refactor:** 코드 리팩토링 `:recycle:`
- 🔧 **Settings:** Changing configuration files `:wrench:`
- 🗃️ **Comment:** 필요한 주석 추가 및 변경 `:card_file_box:`
- ➕ **Dependency/Plugin:** Add a dependency/plugin `:heavy_plus_sign:`
- 📝 **Docs:** 문서 수정 `:memo:`
- 🔀 **Merge:** Merge branches `:twisted_rightwards_arrows:`
- 🚀 **Deploy:** Deploying stuff `:rocket:`
- 🚚 **Rename:** 파일 혹은 폴더명을 수정하거나 옮기는 작업만인 경우 `:truck:`
- 🔥 **Remove:** 파일을 삭제하는 작업만 수행한 경우 `:fire:`
- ⏪️ **Revert:** 전 버전으로 롤백 `:rewind:`

## Commit Convention

- 타입: 커밋 내용
- 예시: `git commit -m ":sparkles: feat: 로그인 기능 구현"`
- 커밋을 생성하거나 수정하기 전에는 반드시 이 문서의 Git Convention과 Commit Convention을 확인할 것.
- 커밋 메시지는 `:<gitmoji_code>: <type>: <한국어 요약>` 형식을 사용할 것.
- 커밋 메시지 검증은 `.githooks/commit-msg`에서 수행하므로, 로컬 저장소의 `core.hooksPath`는 `.githooks`로 설정할 것.

## Issue Convention

- 이슈 제목은 `[타입] 작업 내용` 형식으로 작성할 것.
- 예시: `[Feat] API 구현`
- 이슈를 생성할 때는 작업 유형에 맞는 `.github/ISSUE_TEMPLATE/`의 템플릿을 사용할 것.

## Pull Request Convention

- PR을 생성할 때는 `.github/PULL_REQUEST_TEMPLATE.md`를 참고하여 제목과 본문을 작성할 것.

## Branch Convention

- Conventional Branch v1.1.0 명세를 따른다.
- 형식은 `<type>/<description>`으로 작성한다.
- 일반 작업 브랜치의 설명은 `<issue-number>-<summary>` 형식으로 작성한다.
- 브랜치 타입은 다음 중 하나를 사용한다.
  - `feat`: 새로운 기능
  - `fix`: 버그 수정
  - `hotfix`: 긴급 수정
  - `release`: 릴리스 준비
  - `chore`: 의존성, 문서, 설정 등 비기능 작업
- `feature`와 `bugfix`도 명세상 허용되지만, 일관성을 위해 `feat`와 `fix`를 사용한다.
- `main`, `master`, `develop` 브랜치는 타입 접두사를 사용하지 않는다.
- 설명에는 영문 소문자, 숫자, 하이픈만 사용한다.
- 릴리스 버전에는 점을 사용할 수 있다.
- 연속되거나 설명의 처음 또는 끝에 위치한 하이픈과 점은 허용하지 않는다.
- 하나의 브랜치는 하나의 이슈 또는 작업만 다룬다.
- 예시:
  - `feat/2-user-login`
  - `fix/15-refresh-token-expiration`
  - `hotfix/31-security-patch`
  - `chore/42-update-dependencies`
  - `release/v1.2.0`
