# 배포 파이프라인 전체 흐름

이 문서는 `zipzip-server`가 로컬 개발 환경부터 실제 운영 서버까지 어떤 흐름으로
설정값(환경변수, 시크릿)을 만들고 전달하며, CD 워크플로우가 어떤 절차로 이미지를
빌드하고 서버에 반영하는지를 정리한다.

관련 문서:
- 로컬 개발 환경의 설정 서브모듈 구조는 [git-submodule-config.md](git-submodule-config.md) 참고.
- CI에서 설정값을 만드는 방식과 이유는 [ci-environment-config.md](ci-environment-config.md) 참고.
- 서버에만 존재하는 파일과 배포 스크립트 원문은 [../deploy/README.md](../deploy/README.md) 참고.

이 문서는 위 문서들과 겹치지 않게, **CD(운영/개발 서버 배포) 관점**에서 전체 그림을
하나로 모아 정리하는 데 집중한다.

## 1. 환경 지도

| 환경 | 코드 브랜치 | 워크플로우 | 대상 서버 | DB |
|---|---|---|---|---|
| 로컬 개발 | 아무 브랜치 | 없음 (수동 실행) | 로컬 Docker(postgres) | 로컬 postgres 컨테이너 |
| CI | 모든 push/PR | `ci.yml` | 없음 (GitHub Actions runner 내부) | Actions service container(postgres) |
| dev(개발 서버) | `develop` | `cd-dev.yml` | `zipzip-be` 인스턴스, `api-dev` 컨테이너 | 별도 DB 인스턴스(`zipzip-db`)의 dev 계정/스키마 |
| prod(운영 서버) | `main` | `cd.yml` | `zipzip-be` 인스턴스, `api` 컨테이너 | 별도 DB 인스턴스(`zipzip-db`)의 prod 계정/스키마 |

핵심 포인트:
- **`zipzip-be` 인스턴스 하나(ARM64, Ampere A1)에 prod와 dev 컨테이너가 함께 뜬다.** nginx도
  하나만 떠서 prod/dev 양쪽 요청을 도메인 기준으로 분기한다(`api.zipzip.site` /
  `dev-api.zipzip.site`).
- 애플리케이션 DB는 `zipzip-be`가 아니라 별도 인스턴스(`zipzip-db`)에서 이미 운영 중이라
  compose 스택에 postgres 서비스가 없다(`deploy/docker-compose.yml` 주석 참고).
- 저장소 루트의 `docker-compose.yml`은 **로컬 개발 전용**(postgres 컨테이너 하나)이며
  `deploy/` 아래의 compose 파일들과는 완전히 별개다.

## 2. 각 환경에서 설정값(`application-secret.yml` 등)을 만드는 방식

`application.yaml`은 `spring.config.import: classpath:config/application-secret.yml`로
`application-secret.yml`을 필수 import하기 때문에(옵셔널 아님), 이 파일이 없으면 앱이
아예 기동되지 않는다. 각 환경은 이 파일을 서로 다른 방식으로 채운다.

- **로컬 개발**: private 서브모듈 `src/main/resources/config`(저장소:
  `zipzip-team/zipzip-server-config`)가 실제 `application-secret.yml`,
  `docker-compose.env`를 갖고 있다. 팀원 전체가 같은 값을 서브모듈로 공유한다. 자세한 내용은
  [git-submodule-config.md](git-submodule-config.md).
- **CI(`ci.yml`)**: 서브모듈을 checkout하지 않는다(private 저장소 인증에 CI 성공 여부가
  묶이는 것을 피하기 위함). 대신 GitHub Actions runner 안에서 `mkdir -p
  src/main/resources/config` 후 테스트 전용 DB 접속정보로 `application-secret.yml`을 매번
  새로 생성한다. 값은 워크플로우 상단 `env:`(`CI_DB_NAME`, `CI_DB_USERNAME`,
  `CI_DB_PASSWORD`)에 평문으로 박혀 있는데, 이는 같은 job 안에서 뜨는 임시 postgres service
  container 계정이라 job이 끝나면 사라지는 값이기 때문이다(비밀값 아님). 자세한 근거는
  [ci-environment-config.md](ci-environment-config.md).
- **CD(`cd.yml`, `cd-dev.yml`)의 이미지 빌드 단계**: 여기서도 서브모듈을 checkout하지 않는다.
  대신 **placeholder** `application-secret.yml`(`localhost:5432/placeholder`,
  `placeholder`/`placeholder`)을 만들어 컴파일이 깨지지 않게만 한다. 이 값은 이미지에
  그대로 박히지만, 실제 배포 시 컨테이너가 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`
  환경변수를 주입받아 Spring 프로퍼티 우선순위상 이 값을 덮어쓰므로, public Docker 이미지에
  placeholder 값이 남아 있어도 안전하다.
- **CD의 배포(서버 반영) 단계**: 실제 dev/prod 런타임 환경변수(DB, Apple Login, JWT)는
  GitHub Secrets에서 가져와 SSH로 서버에 전달하고, 서버가 이를 컨테이너 `env_file`로 기록한다(3절 참고).

## 3. CD 워크플로우 상세 흐름 (`cd.yml` = prod, `cd-dev.yml` = dev)

두 워크플로우는 트리거 브랜치와 시크릿 이름 접미사(`_DEV` 유무)만 다르고 구조는 동일하다.

### 3-1. 트리거

- `cd.yml`: `main` 브랜치 push, 또는 `workflow_dispatch` 수동 실행.
- `cd-dev.yml`: `develop` 브랜치 push, 또는 `workflow_dispatch` 수동 실행.
- 둘 다 `concurrency` 그룹으로 동시 실행을 막되(`cancel-in-progress: false`), 진행 중인
  배포를 취소하지는 않고 큐에 쌓는다.

### 3-2. Job 1 — `build-and-push` (이미지 빌드 & 푸시)

1. `actions/checkout@v4`로 코드 체크아웃(서브모듈 옵션 없음 → private config 서브모듈은
   받지 않음).
2. JDK 21(Temurin) + Gradle 설정.
3. 위 2절에서 설명한 placeholder `application-secret.yml` 생성.
4. `./gradlew --no-daemon clean bootJar -x test`로 JAR 빌드. **러너의 네이티브(x86)
   아키텍처로 빌드**한다 — 바이트코드는 아키텍처 무관이라 QEMU 에뮬레이션으로 컴파일할
   필요가 없기 때문(느림). 빌드 산출물은 `app/app.jar`로 복사.
5. QEMU + Docker Buildx 설정, Docker Hub 로그인(`DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN`
   시크릿).
6. `docker/build-push-action@v6`로 **`linux/arm64` 단일 아키텍처만** 빌드 & push. 대상
   인스턴스가 ARM64(Ampere A1)뿐이라서다. 이 단계는 이미 빌드된 JAR을 arm64 JRE 이미지에
   `COPY`/`chown`만 하므로(Dockerfile 참고) QEMU 에뮬레이션이어도 빠르다.
   - prod 이미지 태그: `${DOCKERHUB_USERNAME}/zipzip-be:latest`,
     `${DOCKERHUB_USERNAME}/zipzip-be:${GIT_SHA}`
   - dev 이미지 태그: `${DOCKERHUB_USERNAME}/zipzip-be:dev`,
     `${DOCKERHUB_USERNAME}/zipzip-be:dev-${GIT_SHA}`
   - prod/dev 태그가 절대 겹치지 않게 분리되어 있어 같은 리포지토리(`zipzip-be`) 안에서도
     서로 덮어쓰지 않는다.

### 3-3. Job 2 — `deploy` (SSH로 서버에 반영)

`needs: build-and-push`로 이미지 push가 끝난 뒤에만 실행된다.

**(0) 필수 GitHub Secrets 검증**

- `Validate deployment payload` 단계에서 SSH, DB, Apple Login, JWT 관련 값이 비어 있는지 확인한다.
- `APPLE_PRIVATE_KEY`, `APPLE_PRIVATE_KEY_DEV`는 서버 env 파일에 줄 단위로 기록되므로 newline이 포함되어
  있으면 실패시킨다. 이 값은 단일 라인 PKCS#8 PEM 또는 base64 body 형태로 등록해야 한다.
- 이 검증은 실제 값을 노출하지 않고, 누락된 env 변수 이름만 GitHub Actions 로그에 남긴다.

**(1) SSH 설정**

- prod: `SSH_PRIVATE_KEY` 시크릿을 `~/.ssh/deploy_key`로 기록(`chmod 600`),
  `SSH_HOST`를 known_hosts에 등록.
- dev: `SSH_PRIVATE_KEY_DEV` 시크릿을 `~/.ssh/deploy_key_dev`로 기록. `SSH_HOST`는 prod와
  **동일한 서버**(같은 `zipzip-be` 인스턴스).
- 시크릿은 절대 `run:` 스크립트 문자열에 직접 보간하지 않고 `env:`로만 전달한다. `${{
  secrets.X }}`를 스크립트 본문에 직접 넣으면 GitHub Actions가 워크플로우 텍스트 자체를
  치환해버려서, 시크릿 값 안에 `$(...)`나 백틱이 섞여 있을 경우 그대로 셸 구문으로 실행될
  위험(스크립트 인젝션)이 있기 때문이다. `env:`로 넘기고 `"$VAR"`로 참조하면 값이 단순
  문자열 치환으로만 쓰인다.

**(2) SSH로 배포 명령 실행**

`Validate deployment payload` 단계에서 실제 서버 stdin으로 넘길
`$RUNNER_TEMP/zipzip-deployment.env` 파일을 먼저 만든다. 이때 `append_required_payload`가
각 런타임 env 값을 검증하고 `KEY=value` 라인으로 payload 파일에 기록한다. 이후 배포 단계는
그 파일을 그대로 SSH stdin으로 전달한다.

```bash
append_required_payload SPRING_DATASOURCE_URL "$DB_URL"
append_required_payload APPLE_TEAM_ID "$APPLE_TEAM_ID"
append_required_payload JWT_ACCESS_SECRET "$JWT_ACCESS_SECRET"
append_required_payload JWT_REFRESH_SECRET "$JWT_REFRESH_SECRET"

ssh -i ~/.ssh/deploy_key ... "$SSH_USER@$SSH_HOST" 'deploy' < "$RUNNER_TEMP/zipzip-deployment.env"
```

몇 가지 설계 포인트:

- 서버의 `authorized_keys`에는 이 SSH 키에 대해 **forced command**가 걸려 있어서, 클라이언트가
  실제로 어떤 명령(`'deploy'`)을 보내든 무관하게 서버 쪽에 고정된 스크립트만 실행된다.
  - prod 키 → `~/zipzip-deploy.sh`
  - dev 키 → `~/zipzip-deploy-dev.sh`
  - 두 파이프라인은 SSH 키, GitHub Secrets, 서버 스크립트, 컨테이너/서비스명이 전부 별개다.
- forced command라서 인자로 임의 데이터를 못 받기 때문에, 런타임 환경변수는 **stdin**으로
  흘려보내고 서버 스크립트가 그걸 읽어서 env 파일에 기록한다.
  - prod: `~/zipzip-deploy/zipzip-be.env`
  - dev: `~/zipzip-deploy-dev/.env.dev` (`DOCKERHUB_IMAGE` 값도 함께 보내는데, 이 파일이
    `docker-compose.dev.yml`의 `${DOCKERHUB_IMAGE}` 치환도 겸하기 때문. 매 배포마다 파일을
    통째로 새로 쓰므로 이 값을 빠뜨리면 없어진다.)
- `GIT_SHA`(그 배포를 트리거한 커밋 SHA)도 함께 stdin으로 보낸다. 서버는 이 SHA를 이용해
  **`raw.githubusercontent.com`에서 해당 커밋 시점의 `docker-compose.yml`,
  `nginx/conf.d/*.conf`를 직접 fetch**한다(레포가 public이라 인증 불필요). **compose/nginx
  설정 파일 내용 자체는 stdin으로 절대 보내지 않는다** — 만약 배포 SSH 키가 유출되더라도
  공격자가 임의의 compose 설정(호스트 마운트, `privileged` 등)을 주입할 수는 없고, 기존에
  실제로 리뷰·머지된 커밋 중 하나로만 되돌릴 수 있게 제한하기 위해서다.

**(3) 서버 스크립트가 하는 일** (스크립트 자체는 서버에만 존재, git에는 없음 — 원문은
[../deploy/README.md](../deploy/README.md)에 기록되어 있음)

1. stdin으로 받은 라인들을 한 줄씩 읽어서 `GIT_SHA=` 라인은 변수로 빼내고, 나머지는
   env 파일(prod: `zipzip-be.env`, dev: `.env.dev`)에 그대로 기록.
2. `curl`로 `raw.githubusercontent.com/zipzip-team/zipzip-server/$GIT_SHA/deploy/...`에서
   해당 커밋의 `docker-compose.yml`과 `nginx/conf.d/*.conf`를 받아 로컬 파일을 덮어씀.
3. pull하기 전에 **현재 떠 있는 컨테이너가 쓰던 이미지 ID를 기록**(`docker inspect --format=
   '{{.Image}}'`)해둔 뒤, `docker compose pull api`(또는 dev는 `api-dev`) → `docker compose up
   -d api`로 새 이미지 컨테이너 교체.
4. **헬스체크**: `api`/`api-dev`는 호스트에 포트를 안 열기 때문에, 실제 트래픽과 동일한 경로로
   `nginx` 컨테이너 안에서 `wget`으로 `http://api:8080/actuator/health`(dev는
   `http://api-dev:8080/...`)를 최대 60초(4초 x 15회) 재시도하며 확인한다.
   `spring-boot-starter-actuator`가 이미 의존성에 있어 별도 설정 없이 `/actuator/health`가
   노출된다.
   - **성공** → 5번(nginx reload)으로 진행.
   - **실패**(설정 누락, DB 접속 실패, 마이그레이션 충돌 등 원인 불문) → 3번에서 기록해둔
     이전 이미지 ID를 다시 `:latest`(dev는 `:dev`) 태그로 되돌려 재pull 없이 그 이미지로
     컨테이너를 재생성(롤백)하고, 스크립트가 `exit 1`로 끝나 SSH 커맨드/CD job이 실패로
     표시된다. 서비스는 롤백된 이전 버전으로 계속 정상 동작한다. 최초 배포라 롤백할 이전
     이미지가 없으면 그대로 실패만 알리고 끝난다.
5. `docker compose exec -T nginx nginx -t`로 **문법 검증에 통과했을 때만** `nginx -s reload`.
   검증에 실패하면 reload하지 않고 에러만 출력 — 잘못된 nginx 설정 때문에 서비스 전체가
   죽는 것을 방지.
6. certbot 컨테이너는 이 배포 흐름과 무관하게 별도로 12시간마다 인증서 갱신 루프를 돈다
   (`certbot renew --webroot ... --quiet`).

## 4. PR 단계에서 필수 설정 누락을 잡는 안전장치

### 4-1. 문제: CI green이 "CD 런타임 설정이 갖춰졌다"는 뜻이 아니다

`ci.yml`의 `ZipzipServerApplicationTests`는 `apple.*`, `jwt.*`처럼 새로 추가된 필수
설정(`@Validated` + `@NotBlank`가 붙은 `@ConfigurationProperties` 클래스)에 테스트용 더미
값을 `@SpringBootTest(properties = {...})`로 직접 주입한다. 그래서 실제 GitHub Secrets나
`cd.yml`/`cd-dev.yml`의 서버 전달 로직에 해당 값이 전혀 없어도 PR check는 통과할 수 있다.

CD 빌드 단계(`./gradlew ... bootJar -x test`, 3-2절)는 Spring 컨텍스트를 아예 띄우지
않으므로 여기서도 걸러지지 않는다. 실패는 실제 `develop`/`main` push 이후 서버 컨테이너가
기동하는 시점에야 `BindValidationException`으로 드러난다. 서버 배포 스크립트의 헬스체크와
롤백이 런타임 실패를 마지막 단계에서 방어하지만, 그런 배포 실패를 PR 단계에서 미리 줄이는
안전장치가 필요하다.

### 4-2. 대응: `cd.yml`/`cd-dev.yml`을 손대지 않아도 되는 자동 비교 테스트

[CdRequiredConfigSyncTests.java](../src/test/java/org/zipzip/zipzipserver/CdRequiredConfigSyncTests.java)가
`./gradlew check`(즉 `ci.yml`)에 포함되어 이 갭을 PR 단계에서 잡는다. 핵심은 "필수 설정이
뭔지"와 "CD가 뭘 전달하는지" 두 목록을 **검증 전용으로 새로 만들지 않고**, 각자 다른 실제
목적으로 이미 존재하는 것을 그대로 재사용해 비교한다는 점이다.

1. `ClassPathScanningCandidateComponentProvider`로 `org.zipzip.zipzipserver` 패키지의
   `@ConfigurationProperties` 클래스를 스캔하고, `@NotBlank`/`@NotNull`/`@NotEmpty`가 붙은
   필드를 Spring Boot relaxed binding 규약대로 env var 이름으로 변환한다
   (`apple.team-id` → `APPLE_TEAM_ID`).
2. `.github/workflows/cd.yml`, `cd-dev.yml`의 `Validate deployment payload` 스텝에 있는
   `append_required_payload <KEY> "$<VAR>"` 호출을 텍스트로 파싱해서 실제로 서버에 전달하는
   키 목록을 뽑는다
   (`GIT_SHA`, `DOCKERHUB_IMAGE`는 배포 메타데이터라 제외).
3. 필수 키 집합이 두 워크플로우 각각에서 뽑은 키 집합의 부분집합인지 AssertJ로 assert한다.
   빠진 키가 있으면 실패 메시지에 그 이름을 그대로 노출한다.

`@SpringBootTest`가 아니라 순수 리플렉션 + 텍스트 파싱이라 DB 등 다른 설정과 무관하게
독립적으로 돈다. `develop`에는 아직 `apple.*`/`jwt.*` 같은 필수 설정이 없어서 지금은
트리비얼하게 통과하지만, 그런 설정이 실제로 추가되는 PR부터 자연스럽게 검증 대상이 된다.

### 4-3. 이 방법이 남기는 한계

- PR 단계에서는 `cd.yml`이 해당 키를 **전달하겠다고 선언**했는지만 확인한다. GitHub Secrets의
  실제 값은 PR에서 노출하거나 검증할 수 없기 때문이다. 대신 CD 실행 초반의
  `Validate deployment payload` 단계가 필수 secret 값이 비어 있으면 SSH 전에 실패시킨다.
- 값은 있지만 틀린 경우(오타, 만료 등)는 이 테스트나 CD 초반 non-empty 검증으로는 잡지 못한다.
  이 잔여 리스크는 서버 배포 스크립트의 헬스체크 + 롤백(3-3절 (3) 4번 항목)이 원인 불문하고
  커버한다 — 배포가 실제로 서버에 반영된 뒤 앱이 응답하는지까지 확인하는 건 PR 단계에서는
  원천적으로 할 수 없는 검증이기 때문이다.
- 중첩된(nested object) `@ConfigurationProperties` 필드는 지원하지 않는다 — 현재 코드에
  없는 케이스라 미리 만들지 않았다.

## 5. 서버에만 존재하는 파일 vs 저장소에 있는 파일

| 파일 | 위치 | git 포함 여부 | 내용 |
|---|---|---|---|
| `deploy/docker-compose.yml` | 저장소 | O | prod 스택 정의(변수 참조만, 실제 값 없음) |
| `deploy/docker-compose.dev.yml` | 저장소 | O | dev 스택 정의 |
| `deploy/nginx/conf.d/api.conf` | 저장소 | O | prod nginx 설정 |
| `deploy/nginx/conf.d/dev-api.conf` | 저장소 | O | dev nginx 설정 |
| `~/zipzip-deploy/.env` | 서버(prod) | X | `DOCKERHUB_IMAGE=<username>/zipzip-be` — 시크릿은 아니고 개인 네임스페이스 분리용 |
| `~/zipzip-deploy/zipzip-be.env` | 서버(prod) | X | 실제 prod `SPRING_DATASOURCE_*`, `APPLE_*`, `JWT_*` — 매 배포마다 GitHub Secrets 값으로 덮어써짐 |
| `~/zipzip-deploy-dev/.env.dev` | 서버(dev) | X | 실제 dev `SPRING_DATASOURCE_*`, `APPLE_*`, `JWT_*` + `DOCKERHUB_IMAGE` — 매 배포마다 덮어써짐 |
| `~/zipzip-deploy/certbot/conf` | 서버(prod) | X | Let's Encrypt 발급 인증서 |
| `~/zipzip-deploy.sh`, `~/zipzip-deploy-dev.sh` | 서버 | X | forced command로 실행되는 배포 스크립트 본체(헬스체크 + 자동 롤백 포함) |

## 6. Docker Compose 스택 비교

| | 루트 `docker-compose.yml` | `deploy/docker-compose.yml`(prod) | `deploy/docker-compose.dev.yml`(dev) |
|---|---|---|---|
| 용도 | 로컬 개발용 DB | 운영 서버 스택 | 운영 서버 위의 dev 스택 |
| 서비스 | `postgres` | `api`, `nginx`, `certbot` | `api-dev` |
| 이미지 | `${POSTGRES_IMAGE}`(로컬 `docker-compose.env`로 치환) | `${DOCKERHUB_IMAGE}:latest` | `${DOCKERHUB_IMAGE}:dev` |
| 네트워크 | 기본 | `zipzip-net`(bridge, 새로 생성) | `zipzip-net`을 **external**로 참조(`zipzip-deploy_zipzip-net`) — prod가 만든 네트워크에 얹혀서 nginx가 그대로 라우팅 |
| 포트 노출 | 호스트에 `5432` 노출(로컬 개발이라 직접 접속 필요) | `api`는 미노출(`expose`만, nginx 통해서만 접근), `nginx`만 80/443 | `api-dev`는 미노출, 별도 nginx 없이 prod nginx가 프록시 |
| DB | 이 스택 안에 있음 | 없음 — 별도 인스턴스(`zipzip-db`)에 이미 존재 | 없음 — 별도 인스턴스의 dev 계정/스키마 |

dev 스택이 별도 nginx를 안 띄우고 prod의 `zipzip-net`(compose 프로젝트명이 접두어로 붙어
실제로는 `zipzip-deploy_zipzip-net`)에 `external: true`로 붙는 이유는, nginx/인증서
인프라를 dev용으로 중복 구축하지 않기 위해서다.

## 7. nginx 라우팅

- prod: `api.zipzip.site` → 80은 ACME challenge + 443 리다이렉트만, 443은 TLS 종료 후
  `proxy_pass http://api:8080`(같은 compose 스택 안이라 서비스명으로 바로 접근 가능,
  시작 순서도 `depends_on: api`로 보장됨).
- dev: `dev-api.zipzip.site` → 443은 TLS 종료 후 `http://api-dev:8080`으로 프록시하되,
  **`resolver 127.0.0.11 valid=10s`와 변수(`set $api_dev_upstream`)를 이용해 요청 시점에
  동적으로 upstream을 resolve**한다. `api-dev`는 별도 compose 스택(`docker-compose.dev.yml`)
  에서 뜨기 때문에 nginx와 기동 순서가 보장되지 않는데, `proxy_pass`에 호스트명을 직접 쓰면
  nginx가 설정 로드 시점에 upstream을 해석하려다 컨테이너가 아직 없으면 reload 자체가
  실패한다. 변수+resolver 방식은 `api-dev`가 잠깐 꺼져 있어도 nginx는 안 죽고 502만
  내려주게 한다.
- TLS 인증서는 prod 도메인 기준으로 최초 1회 발급(webroot 방식, `certbot certonly`)했고
  이후 certbot 컨테이너가 12시간 주기로 자동 갱신한다. dev 도메인은 같은 인증서
  (`api.zipzip.site`)의 `ssl_certificate`를 그대로 참조하고 있음(설정 파일 그대로).

## 8. GitHub Secrets 총정리

| 시크릿 이름 | 사용 워크플로우 | 용도 |
|---|---|---|
| `DOCKERHUB_USERNAME` | cd.yml, cd-dev.yml | 이미지 이름(`${DOCKERHUB_USERNAME}/zipzip-be`) 및 로그인 |
| `DOCKERHUB_TOKEN` | cd.yml, cd-dev.yml | Docker Hub 로그인 |
| `SSH_HOST` | cd.yml, cd-dev.yml | 배포 대상 서버(prod/dev 공유) |
| `SSH_USER` | cd.yml, cd-dev.yml | SSH 접속 계정 |
| `SSH_PRIVATE_KEY` | cd.yml | prod 전용 배포 키(forced command → `zipzip-deploy.sh`) |
| `SSH_PRIVATE_KEY_DEV` | cd-dev.yml | dev 전용 배포 키(forced command → `zipzip-deploy-dev.sh`) |
| `SPRING_DATASOURCE_URL` | cd.yml | prod DB 접속 URL |
| `SPRING_DATASOURCE_USERNAME` | cd.yml | prod DB 계정 |
| `SPRING_DATASOURCE_PASSWORD` | cd.yml | prod DB 비밀번호 |
| `SPRING_DATASOURCE_URL_DEV` | cd-dev.yml | dev DB 접속 URL |
| `SPRING_DATASOURCE_USERNAME_DEV` | cd-dev.yml | dev DB 계정 |
| `SPRING_DATASOURCE_PASSWORD_DEV` | cd-dev.yml | dev DB 비밀번호 |
| `APPLE_TEAM_ID` | cd.yml | prod Apple Developer Team ID |
| `APPLE_CLIENT_ID` | cd.yml | prod Apple Login client id |
| `APPLE_KEY_ID` | cd.yml | prod Apple private key id |
| `APPLE_PRIVATE_KEY` | cd.yml | prod Apple client secret 서명용 private key |
| `JWT_ISSUER` | cd.yml | prod JWT issuer |
| `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET` | cd.yml | prod JWT 서명 secret |
| `JWT_ACCESS_TOKEN_EXPIRATION` | cd.yml | prod access token 만료 시간 |
| `JWT_REFRESH_TOKEN_EXPIRATION` | cd.yml | prod refresh token 만료 시간 |
| `APPLE_TEAM_ID_DEV` | cd-dev.yml | dev Apple Developer Team ID |
| `APPLE_CLIENT_ID_DEV` | cd-dev.yml | dev Apple Login client id |
| `APPLE_KEY_ID_DEV` | cd-dev.yml | dev Apple private key id |
| `APPLE_PRIVATE_KEY_DEV` | cd-dev.yml | dev Apple client secret 서명용 private key |
| `JWT_ISSUER_DEV` | cd-dev.yml | dev JWT issuer |
| `JWT_ACCESS_SECRET_DEV`, `JWT_REFRESH_SECRET_DEV` | cd-dev.yml | dev JWT 서명 secret |
| `JWT_ACCESS_TOKEN_EXPIRATION_DEV` | cd-dev.yml | dev access token 만료 시간 |
| `JWT_REFRESH_TOKEN_EXPIRATION_DEV` | cd-dev.yml | dev refresh token 만료 시간 |

`APPLE_PRIVATE_KEY`, `APPLE_PRIVATE_KEY_DEV`는 deployment payload와 서버 env 파일이 줄 단위로 전달·기록되므로
raw multiline PEM이 아니라 **단일 라인 PKCS#8 PEM** 또는 **base64 body** 형태로 등록해야 한다. CD의
`Validate deployment payload` 단계는 값이 비어 있거나 newline을 포함하면 SSH 전에 실패한다.

## 9. 브랜치 → 배포 트리거 매핑 요약

```
develop  --push-->  cd-dev.yml  --build/push image(:dev, :dev-{sha})-->  Docker Hub
                                 --SSH(SSH_PRIVATE_KEY_DEV)-->  zipzip-be 서버
                                     forced command: ~/zipzip-deploy-dev.sh
                                     → .env.dev 갱신, docker-compose.dev.yml/dev-api.conf를
                                       해당 커밋 기준 raw.githubusercontent.com에서 fetch
                                     → docker compose pull/up api-dev
                                     → (prod) nginx -t 통과 시 reload

main     --push-->  cd.yml      --build/push image(:latest, :{sha})-->  Docker Hub
                                 --SSH(SSH_PRIVATE_KEY)-->  zipzip-be 서버
                                     forced command: ~/zipzip-deploy.sh
                                     → zipzip-be.env 갱신, docker-compose.yml/api.conf를
                                       해당 커밋 기준 raw.githubusercontent.com에서 fetch
                                     → docker compose pull/up api
                                     → nginx -t 통과 시 reload
```

두 워크플로우 모두 `workflow_dispatch`로 수동 재실행이 가능하다(예: 이미지는 그대로 두고
서버 설정만 최신 커밋 기준으로 다시 반영하고 싶을 때).

## 10. 보안 설계 핵심 요약

- **placeholder secret + 런타임 env var 우선순위**: 빌드 시점 `application-secret.yml`은
  가짜 값이고, 컨테이너 기동 시 `SPRING_DATASOURCE_*` 환경변수가 이를 덮어쓰므로 public
  이미지에 가짜 값이 남아 있어도 문제없다.
- **시크릿은 `env:`로만 전달, 스크립트 문자열에 직접 보간 금지**: 워크플로우 텍스트 치환에
  의한 셸 인젝션을 방지.
- **forced command SSH 키**: 배포 키가 유출돼도 클라이언트가 임의 명령을 실행할 수 없고,
  서버에 고정된 스크립트만 실행됨.
- **compose/nginx 설정은 stdin이 아니라 `GIT_SHA` 기준 `raw.githubusercontent.com` fetch**:
  키가 유출되어도 공격자는 실제로 리뷰·머지된 과거 커밋으로만 되돌릴 수 있을 뿐, 임의의
  compose 설정(호스트 마운트, `privileged` 등)을 주입할 수 없음.
- **`nginx -t` 통과 시에만 reload**: 잘못된 nginx 설정으로 서비스 전체가 죽는 것을 방지.
- **prod/dev 이미지 태그, SSH 키, 시크릿, 서버 스크립트, 컨테이너명 완전 분리**: 한쪽 파이프라인의
  문제가 다른 쪽에 번지지 않도록 격리.
- **배포 스크립트의 헬스체크 + 자동 롤백**(3-3절 (3) 4번): 새 컨테이너가 60초 안에 응답하지
  않으면 원인 불문 이전 이미지로 자동 복구하고 배포 자체는 실패로 표시한다. PR 단계
  검증(4절)이 못 잡는 "값은 있지만 틀린 경우"까지 포함해 실제 서비스 중단을 방지하는
  마지막 안전망이다.
- **PR 단계에서 필수 설정 ↔ CD 전달 키 동기화 검증**(4절): CI가 더미 값으로 통과시키는 새
  필수 설정이 실제로는 `cd.yml`/`cd-dev.yml`에서 서버로 전달되지 않는 채로 머지되는 것을
  막는다.
