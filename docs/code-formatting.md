# 코드 포매팅 가이드

이 프로젝트는 Java 코드 포매터로 Spotless와 `google-java-format`의 AOSP 모드를 사용한다.
기준 스타일은 Android Open Source Project의 Java 코드 스타일을 따른다.

- 기준 문서: [Android Code Style](https://source.android.com/docs/setup/contribute/code-style)
- Gradle 플러그인: `com.diffplug.spotless`
- Java 포매터: `google-java-format`
- 적용 모드: AOSP 모드

## 기본 규칙

- 들여쓰기는 공백 4칸을 사용한다.
- 줄바꿈 후 이어지는 행의 들여쓰기는 공백 8칸을 사용한다.
- 한 줄은 100자를 넘기지 않는 것을 기준으로 한다.
- 파일 끝에는 마지막 개행을 둔다.
- 줄 끝의 불필요한 공백은 제거한다.
- 사용하지 않는 import는 제거한다.
- Java 코드의 import 정렬과 중괄호 배치는 `google-java-format` 결과를 따른다.

## Gradle 명령

Gradle 명령은 JDK 21로 실행한다.

현재 로컬 기본 JDK가 21이 아닌 경우 `JAVA_HOME`을 JDK 21 경로로 지정한 뒤 실행한다.

```bash
./gradlew spotlessApply
```

위 명령은 포매팅 규칙을 현재 코드에 적용한다.

```bash
./gradlew spotlessCheck
```

위 명령은 포매팅 규칙 위반이 있는지 확인한다.
코드를 변경한 뒤 커밋하기 전에 실행하는 것을 권장한다.

```bash
./gradlew check
```

위 명령은 포매팅 검사와 테스트를 포함한 전체 검증을 실행한다.

## 적용 대상

### Java

`src/main/java`와 `src/test/java`의 Java 파일은 `google-java-format` AOSP 모드로 정리한다.

현재 설정은 다음 항목을 포함한다.

- AOSP 스타일 포맷 적용
- 긴 문자열 재흐름 처리
- 사용하지 않는 import 제거
- 줄 끝 공백 제거
- 파일 끝 개행 유지

### 기타 파일

다음 파일도 공통 공백 규칙을 적용한다.

- `*.gradle`
- `*.md`
- `*.yml`
- `*.yaml`
- `.gitignore`
- `.env.example`

기타 파일에는 줄 끝 공백 제거와 파일 끝 개행 유지 규칙이 적용된다.

## IDE 설정

저장소 루트의 `.editorconfig`를 IDE에서 인식하도록 설정한다.

주요 설정은 다음과 같다.

- 모든 파일은 UTF-8을 사용한다.
- 줄바꿈은 LF를 사용한다.
- Java 파일은 공백 4칸 들여쓰기를 사용한다.
- Java 파일의 continuation indent는 공백 8칸을 사용한다.
- Java 파일의 기준 줄 길이는 100자이다.

IDE의 자체 포매터 결과가 Spotless 결과와 다를 수 있으므로, 최종 기준은 항상
`./gradlew spotlessApply` 결과로 판단한다.

## 커밋 전 확인 절차

커밋 전에는 다음 순서로 확인한다.

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
```

테스트까지 함께 확인해야 하는 변경이라면 다음 명령도 실행한다.

```bash
./gradlew check
```

`spotlessCheck`가 실패하면 `spotlessApply`를 실행해 포맷을 적용한 뒤 다시 확인한다.

## CI 권장 사항

CI에서는 최소한 다음 명령을 실행해 포매팅 위반을 막는다.

```bash
./gradlew spotlessCheck
```

전체 빌드 검증 단계에서는 다음 명령을 사용할 수 있다.

```bash
./gradlew check
```
