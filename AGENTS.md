# AGENTS.md

## 기본 규칙

- 모든 문서는 한국어로 작성할 것.
- 사용자가 명시적으로 요청하지 않은 파일은 임의로 수정하지 말 것.
- 특히 `README.md`처럼 프로젝트 첫 화면이나 문서 인덱스 역할을 하는 파일에는 사용자의 명시적인 요청 없이 링크, 안내 문구, 섹션을 추가하지 말 것.

## Gradle 실행 규칙

- Gradle 명령은 JDK 21로 실행할 것.
- 현재 로컬 기본 JDK가 21이 아닐 수 있으므로, 필요하면 `JAVA_HOME`을 JDK 21 경로로 지정해서 실행할 것.

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

## Branch Convention

- 브랜치의 경우 이슈 번호를 붙여 작성합니다.
- 형식: 타입/이슈번호-구현할 기능
- 예시: `feat/2-login`
