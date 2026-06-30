# CI 환경 설정 전략

이 문서는 GitHub Actions CI 파이프라인에서 애플리케이션 설정값을 어떻게 주입할지에
대한 결정 내용을 정리한다.

## 배경

이 프로젝트는 민감 설정을 메인 저장소에 직접 커밋하지 않고,
`src/main/resources/config` 서브모듈로 분리해서 관리한다.

로컬 개발에서 이 방식은 다음 장점이 있다.

- 팀원이 같은 설정 파일을 받을 수 있다.
- `.env` 파일을 각자 따로 만들고 메신저로 공유하는 번거로움을 줄인다.
- `application-secret.yml`과 `docker-compose.env`를 같은 저장소에서 버전 관리할 수 있다.
- 설정 변경 이력과 리뷰, 롤백이 가능하다.

따라서 로컬 개발 환경에서는 기존 서브모듈 방식을 유지한다.

## CI에서 서브모듈을 직접 사용하지 않는 이유

CI에서도 private 서브모듈을 checkout하면 로컬과 CI가 같은 설정 파일을 쓴다는 장점이
있다. 하지만 CI 성공 여부가 애플리케이션 코드뿐 아니라 private 저장소 접근 상태에도
묶인다.

예를 들어 다음 문제가 생기면 코드가 정상이어도 CI가 실패할 수 있다.

- private 서브모듈 저장소 접근 권한 변경
- checkout에 사용하는 PAT 또는 deploy key 만료
- 토큰 권한 범위 변경
- 서브모듈 포인터가 가리키는 커밋 접근 실패
- GitHub Actions에서 private submodule 인증 실패

CI의 주된 목적은 포매팅, 컴파일, 테스트, Spring context 로딩을 안정적으로 검증하는
것이다. 따라서 CI는 private 설정 저장소 인증에 의존하지 않고, 테스트에 필요한 최소
설정만 자체적으로 만든다.

## 결정

CI에서는 서브모듈을 checkout하지 않는다.

대신 GitHub Actions runner 안에서 테스트용 `application-secret.yml`을 임시 생성한다.
이 파일은 Git에 커밋되지 않으며, workflow 실행이 끝나면 runner와 함께 사라진다.

로컬 개발과 CI의 역할은 다음처럼 나눈다.

- 로컬 개발 환경 통일: 서브모듈의 `application-secret.yml`, `docker-compose.env` 사용
- CI 테스트 실행 환경: workflow에서 테스트용 `application-secret.yml` 임시 생성
- 운영 또는 배포 secret: 이후 CD 파이프라인에서 GitHub Secrets 또는 배포 환경 secret 사용

## CI용 값이 평문이어도 되는 이유

CI에서 사용하는 PostgreSQL 계정은 실제 운영 DB 계정이 아니다.

GitHub Actions job 안에서 service container로 PostgreSQL을 새로 띄우고, job이 끝나면
컨테이너도 함께 사라진다. 따라서 다음 값은 외부 시스템에 접근하는 비밀값이 아니라
CI 내부에서만 쓰는 테스트용 임시 값이다.

```yaml
POSTGRES_DB: zipzip_server_test
POSTGRES_USER: test_zipzip
POSTGRES_PASSWORD: test_zipzip_password
```

이런 값은 workflow 파일에 평문으로 둘 수 있다.

반대로 다음 값이 필요해지면 workflow에 평문으로 작성하지 않고 GitHub Secrets 또는
배포 환경 secret으로 관리한다.

- 운영 또는 개발 DB 비밀번호
- JWT signing secret
- OAuth client secret
- AWS, S3 같은 클라우드 access key
- 외부 API key
- private 서브모듈 checkout token

## 권장 workflow 구조

CI는 PostgreSQL service container를 띄운 뒤, 같은 DB 값으로
`src/main/resources/config/application-secret.yml`을 생성한다.

DB 이름, 사용자 이름, 비밀번호는 service container와 설정 파일 생성 step에서 모두
사용하므로 workflow 상단 `env`에 한 번만 정의한다.

```yaml
name: CI

on:
  push:
  pull_request:
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: ci-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

env:
  CI_DB_NAME: zipzip_server_test
  CI_DB_USERNAME: test_zipzip
  CI_DB_PASSWORD: test_zipzip_password

jobs:
  build:
    name: Build and test
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:18
        env:
          POSTGRES_DB: ${{ env.CI_DB_NAME }}
          POSTGRES_USER: ${{ env.CI_DB_USERNAME }}
          POSTGRES_PASSWORD: ${{ env.CI_DB_PASSWORD }}
        ports:
          - 5432:5432
        options: >-
          --health-cmd "pg_isready -U $POSTGRES_USER -d $POSTGRES_DB"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Create CI application secret
        run: |
          mkdir -p src/main/resources/config
          cat > src/main/resources/config/application-secret.yml <<EOF
          spring:
            datasource:
              url: jdbc:postgresql://localhost:5432/${CI_DB_NAME}
              username: ${CI_DB_USERNAME}
              password: ${CI_DB_PASSWORD}
              driver-class-name: org.postgresql.Driver
          EOF

      - name: Run verification
        run: ./gradlew --no-daemon check
```

## Docker Compose 환경변수 처리

CI에서는 `docker-compose.yml`을 실행하지 않는다.

PostgreSQL은 GitHub Actions의 `services.postgres`로 실행하므로,
`docker-compose.env`를 만들거나 주입할 필요가 없다.

`docker-compose.env`는 로컬 개발에서 다음 명령으로 PostgreSQL을 실행할 때 사용한다.

```bash
docker compose --env-file src/main/resources/config/docker-compose.env up -d
```

## 검증 범위

CI의 기본 검증 명령은 다음과 같다.

```bash
./gradlew --no-daemon check
```

이 명령은 현재 프로젝트 기준으로 다음 검증을 포함한다.

- Spotless 포매팅 검사
- Java 컴파일
- 테스트 실행
- Spring Boot context 로딩
- CI PostgreSQL service container 연결 확인

## 브랜치 전략과 CI 실행 시점

이 프로젝트는 simplified Git Flow를 사용한다.

- 기능 개발은 `feat/{issue-number}-{feature-name}` 브랜치에서 진행한다.
- 기능 브랜치는 `develop`으로 머지한다.
- `main`은 배포 브랜치로 사용한다.

CI는 브랜치 필터 없이 `push`, `pull_request`, 수동 실행에서 동작하게 둔다. 이렇게 하면
`feat/*`, `develop`, `main` 어느 브랜치에서도 동일한 검증 기준을 적용할 수 있다.
