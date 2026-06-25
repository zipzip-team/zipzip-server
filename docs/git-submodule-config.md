# Git 서브모듈 설정 가이드

이 문서는 `alty-server`에서 민감 설정 파일을 Git 서브모듈로 관리하는 방법을 정리한다.

## 목적

DB 접속 정보, 토큰 시크릿, 외부 서비스 키처럼 공개 저장소에 올리면 안 되는 값은
메인 서버 저장소와 분리해서 관리한다.

이 프로젝트는 민감 설정을 별도 private 저장소인
`alty-team/alty-server-config`에 두고, 메인 저장소에서는 해당 저장소를 서브모듈로
참조한다.

## 현재 구조

```text
src/main/resources/
├── application.yaml
├── config/
│   ├── application-secret.yml
│   └── docker-compose.env
├── static/
└── templates/
```

`application.yaml`은 메인 저장소에서 관리한다.

`src/main/resources/config` 디렉터리는 private 설정 저장소를 가리키는 Git 서브모듈이다.
이 디렉터리 안의 실제 파일 내용은 메인 저장소에 저장되지 않고, 서브모듈 저장소에서
관리된다.

서브모듈 설정은 `.gitmodules`에 기록되어 있다.

```ini
[submodule "src/main/resources/config"]
    path = src/main/resources/config
    url = https://github.com/alty-team/alty-server-config.git
```

## application.yaml과 application-secret.yml의 역할

Spring Boot의 기본 설정 파일은 다음 위치에 둔다.

```text
src/main/resources/application.yaml
```

`config` 디렉터리는 Spring Boot 기본 생성 위치가 아니라, 민감 설정을 분리하기 위해
프로젝트에서 별도로 정한 위치다.

현재 `application.yaml`은 다음 설정을 통해 private 설정 파일을 import한다.

```yaml
spring:
    config:
        import: classpath:config/application-secret.yml
```

이 설정 때문에 애플리케이션 시작 시 Spring Boot는 다음 파일도 함께 읽는다.

```text
src/main/resources/config/application-secret.yml
```

## docker-compose.yml과 docker-compose.env의 역할

`docker-compose.yml`은 메인 저장소에서 관리한다.

```text
docker-compose.yml
```

이 파일에는 실제 값 대신 다음처럼 변수 참조만 둔다.

```yaml
services:
    postgres:
        image: ${POSTGRES_IMAGE}
        ports:
            - "${POSTGRES_PORT}:5432"
        environment:
            POSTGRES_DB: ${POSTGRES_DB}
            POSTGRES_USER: ${POSTGRES_USER}
            POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
```

실제 Docker Compose 실행 값은 private 서브모듈 안의 다음 파일에서 관리한다.

```text
src/main/resources/config/docker-compose.env
```

예시는 다음과 같다.

```env
POSTGRES_IMAGE=postgres:18
POSTGRES_PORT=5432
POSTGRES_DB=alty_server
POSTGRES_USER=alty
POSTGRES_PASSWORD=secret_password
```

`docker-compose.env`는 Spring Boot가 읽는 파일이 아니라 Docker Compose CLI가 읽는
파일이다. 따라서 컨테이너를 실행할 때는 env 파일을 명시해서 전달한다.

```bash
docker compose --env-file src/main/resources/config/docker-compose.env up -d
```

최종 Compose 설정이 올바르게 해석되는지 먼저 확인하려면 다음 명령을 사용한다.

```bash
docker compose --env-file src/main/resources/config/docker-compose.env config
```

컨테이너를 내릴 때도 같은 env 파일을 넘기는 편이 안전하다.

```bash
docker compose --env-file src/main/resources/config/docker-compose.env down
```

`env_file` 속성을 `docker-compose.yml`에 추가하는 방식과 위 명령은 역할이 다르다.
`env_file`은 주로 컨테이너 내부에 환경변수를 주입할 때 사용한다. 반면
`${POSTGRES_IMAGE}`, `${POSTGRES_PORT}`처럼 Compose 파일 자체를 해석하는 데 필요한
값은 `--env-file`로 전달한다.

## IntelliJ 실행 설정

IntelliJ의 Run/Debug Configuration에는 datasource 환경변수를 따로 등록하지 않는다.

제거 대상은 다음과 같다.

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

Spring Boot 애플리케이션은 `application.yaml`의 `spring.config.import` 설정을 통해
`application-secret.yml`을 자동으로 읽는다. 따라서 IntelliJ에서 애플리케이션을 실행할
때는 별도의 datasource 환경변수를 주입하지 않아도 된다.

오히려 Run/Debug Configuration에 datasource 환경변수가 남아 있으면
`application-secret.yml`의 값보다 우선 적용되어 설정 변경이 반영되지 않는 것처럼
보일 수 있다.

`docker-compose.env`와 `application-secret.yml`의 DB 이름, 포트, 사용자 이름, 비밀번호는
서로 맞춰야 한다.

## optional을 사용하지 않는 이유

현재 프로젝트는 다음처럼 `optional:`을 붙이지 않는다.

```yaml
spring:
    config:
        import: classpath:config/application-secret.yml
```

`optional:`을 붙이지 않으면 `application-secret.yml`이 없을 때 애플리케이션이 실행되지
않는다.

이 프로젝트는 `.env`나 `.env.example` 없이 private 서브모듈 설정 파일을 기준으로
실행하는 방향을 사용한다. 따라서 설정 파일이 없는 상태로 애플리케이션이 잘못 실행되는
것보다, 시작 단계에서 명확히 실패하는 편이 낫다.

반대로 다음처럼 작성하면 설정 파일이 없어도 실행을 시도한다.

```yaml
spring:
    config:
        import: optional:classpath:config/application-secret.yml
```

이 방식은 환경변수 fallback을 함께 사용할 때 적합하다. 현재 프로젝트 정책과는 맞지
않으므로 사용하지 않는다.

## 설정 우선순위

`application-secret.yml`에 `application.yaml`과 같은 설정 키가 있으면,
import된 `application-secret.yml`의 값이 적용된다.

예를 들어 `application.yaml`에 다음 설정이 있고,

```yaml
spring:
    datasource:
        driver-class-name: org.postgresql.Driver
```

`application-secret.yml`에 다음 설정이 있으면,

```yaml
spring:
    datasource:
        url: jdbc:postgresql://localhost:5432/alty
        username: alty_user
        password: secret_password
```

Spring Boot는 두 파일을 합쳐서 최종 설정을 만든다.

```yaml
spring:
    datasource:
        driver-class-name: org.postgresql.Driver
        url: jdbc:postgresql://localhost:5432/alty
        username: alty_user
        password: secret_password
```

주의할 점은 값이 비어 있는 설정도 override 대상으로 처리될 수 있다는 점이다.

```yaml
spring:
    datasource:
        username:
        password:
```

아직 값을 넣지 않은 설정은 빈 값으로 두지 말고 아예 작성하지 않거나 주석 처리한다.

```yaml
# spring:
#     datasource:
#         username:
#         password:
```

## 처음 저장소를 clone하는 경우

메인 저장소를 처음 받을 때는 서브모듈까지 함께 clone한다.

```bash
git clone --recurse-submodules https://github.com/alty-team/alty-server.git
```

`--recurse-submodules`를 빼고 clone하면 `src/main/resources/config` 안의 실제 설정
파일이 내려오지 않는다.

## 이미 clone한 저장소에서 서브모듈 받기

이미 메인 저장소를 clone한 상태라면 다음 명령으로 서브모듈을 초기화하고 파일을 받는다.

```bash
git submodule update --init --recursive
```

이 명령을 실행하면 `.gitmodules`에 기록된
`https://github.com/alty-team/alty-server-config.git` 저장소가
`src/main/resources/config` 경로에 연결된다.

## 설정 파일 변경하기

`application-secret.yml` 또는 `docker-compose.env`를 수정할 때는 서브모듈 디렉터리
안에서 커밋한다.

```bash
cd src/main/resources/config
git status
git add application-secret.yml docker-compose.env
git commit -m ":wrench: settings: 설정 값 수정"
git push origin main
```

그 다음 메인 저장소로 돌아와서 서브모듈 포인터 변경을 커밋한다.

```bash
cd ../../../..
git status
git add src/main/resources/config
git commit -m ":wrench: settings: 설정 서브모듈 포인터 갱신"
```

서브모듈은 메인 저장소에 파일 내용을 직접 저장하지 않고, 특정 커밋을 가리키는 포인터만
저장한다. 따라서 private 설정 저장소를 수정한 뒤에는 메인 저장소에서도 포인터 변경을
커밋해야 다른 팀원이 같은 설정 버전을 받을 수 있다.

## 최신 설정 가져오기

다른 팀원이 설정 저장소를 업데이트했다면 다음 명령으로 최신 커밋을 가져온다.

```bash
git submodule update --remote
```

이 명령은 서브모듈의 원격 브랜치 최신 커밋을 가져온다.
이후 메인 저장소에서 `git status`를 확인했을 때 `src/main/resources/config`가 변경된
것으로 보이면, 필요한 경우 포인터 변경을 커밋한다.

```bash
git status
git add src/main/resources/config
git commit -m ":wrench: settings: 설정 서브모듈 최신화"
```

## 자주 쓰는 명령

```bash
# 서브모듈 상태 확인
git submodule status
```

```bash
# clone 후 서브모듈 초기화
git submodule update --init --recursive
```

```bash
# 원격 저장소 기준으로 서브모듈 최신화
git submodule update --remote
```

```bash
# 서브모듈 내부 변경 확인
cd src/main/resources/config
git status
```

## 주의사항

- `application-secret.yml`의 실제 값은 메인 저장소에 직접 작성하지 않는다.
- `docker-compose.env`의 실제 값도 메인 저장소에 직접 작성하지 않는다.
- `src/main/resources/config` 안에서 발생한 변경은 서브모듈 저장소의 변경이다.
- 서브모듈 저장소에서 커밋과 push를 먼저 한 뒤, 메인 저장소에서 포인터 변경을 커밋한다.
- `application-secret.yml`이 없으면 애플리케이션은 실행되지 않는다.
- Docker Compose 실행 시에는 `--env-file src/main/resources/config/docker-compose.env`를
  명시한다.
- private 설정 저장소 접근 권한이 없는 팀원은 서브모듈을 받을 수 없다.
- 값이 비어 있는 YAML 키는 의도치 않게 기존 설정을 덮어쓸 수 있으므로 작성하지 않는다.
