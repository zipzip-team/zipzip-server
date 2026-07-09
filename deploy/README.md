# zipzip-be 프로덕션 배포 스택

`zipzip-be` 인스턴스(`~/zipzip-deploy`)에서 실행되는 compose 스택 구성입니다. 저장소 루트의
`docker-compose.yml`(로컬 개발용 postgres 전용)과는 별개입니다.

## 구성

- `api`: `${DOCKERHUB_IMAGE}:latest` — 스프링 부트 애플리케이션. 호스트에 포트를 노출하지 않고
  `zipzip-net` 내부망에서만 nginx가 접근합니다.
- `nginx`: 80/443 리버스 프록시, TLS 종료. 80은 인증서 갱신용 challenge와 443 리다이렉트만 처리.
- `certbot`: Let's Encrypt 인증서 자동 갱신 루프(`certbot renew`를 12시간마다 실행).

## 서버에 필요한 파일 (git에 없음, 서버에만 존재)

- `~/zipzip-deploy/.env`: compose 변수 치환용. `DOCKERHUB_IMAGE=<dockerhub-username>/zipzip-be` (형식은
  `.env.example` 참고). 개인 계정명을 커밋된 파일에 박아두지 않기 위해 분리함 — 시크릿은 아니고
  단순히 개인 네임스페이스를 코드에서 분리하기 위한 목적.
- `~/zipzip-deploy/zipzip-be.env`: 프로덕션 런타임 환경변수(`SPRING_DATASOURCE_*`, `APPLE_*`, `JWT_*`).
  git/이미지에 절대 포함하지 않음. CD가 배포할 때마다 GitHub Secrets 값으로 덮어씀(아래 배포 흐름 참고).
- `~/zipzip-deploy/certbot/conf`: 발급된 인증서.

## 배포 흐름

CD(`​.github/workflows/cd.yml`)가 이미지를 Docker Hub에 push한 뒤, 서버의 CD 전용 SSH 배포키로 접속합니다.
이 키는 `authorized_keys`에 forced command로 제한되어 있어 실제로는 클라이언트가 보낸 명령과 무관하게
`~/zipzip-deploy.sh`만 실행됩니다.

**런타임 환경변수**는 GitHub Secrets에 개별 등록해두고, forced command라 인자로 못 넘기니
**stdin으로 흘려보내** 스크립트가 `zipzip-be.env`에 기록합니다.

prod CD에 필요한 Secrets:

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `APPLE_TEAM_ID`, `APPLE_CLIENT_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`
- `JWT_ISSUER`, `JWT_SECRET`, `JWT_ACCESS_TOKEN_EXPIRATION`, `JWT_REFRESH_TOKEN_EXPIRATION`

dev CD는 동일한 애플리케이션 env key를 서버 `.env.dev`에 기록하되, GitHub Secrets 이름은
`*_DEV` 접미사를 사용합니다.

- `SPRING_DATASOURCE_URL_DEV`, `SPRING_DATASOURCE_USERNAME_DEV`, `SPRING_DATASOURCE_PASSWORD_DEV`
- `APPLE_TEAM_ID_DEV`, `APPLE_CLIENT_ID_DEV`, `APPLE_KEY_ID_DEV`, `APPLE_PRIVATE_KEY_DEV`
- `JWT_ISSUER_DEV`, `JWT_SECRET_DEV`, `JWT_ACCESS_TOKEN_EXPIRATION_DEV`, `JWT_REFRESH_TOKEN_EXPIRATION_DEV`

`APPLE_PRIVATE_KEY(_DEV)`는 서버 env 파일과 deployment payload 전달 경로가 줄 단위로 동작하므로 raw multiline
PEM이 아니라 **단일 라인 PKCS#8 PEM** 또는 **base64 body** 형태로 등록해야 합니다.

**`docker-compose.yml`/`nginx/conf.d/api.conf`**는 stdin으로 파일 내용을 직접 보내지 않습니다 — 그러면
배포키가 유출됐을 때 임의 compose 설정(호스트 마운트, `privileged` 등)을 주입당할 위험이 있기 때문입니다.
대신 CD가 `GIT_SHA`(그 배포를 트리거한 커밋)만 stdin으로 보내고, 서버가 **`raw.githubusercontent.com`에서
해당 커밋의 파일을 직접 fetch**합니다(레포가 public이라 인증 불필요). 이러면 실제로 리뷰·머지된 커밋의
내용만 반영될 수 있고, 키만 유출된 공격자는 기존에 존재하는 커밋으로만 되돌릴 수 있을 뿐 임의 내용을
주입할 수 없습니다.

```bash
#!/bin/bash
set -e
cd "$HOME/zipzip-deploy"

REPO_RAW="https://raw.githubusercontent.com/zipzip-team/zipzip-server"
GIT_SHA=""
: > zipzip-be.env.tmp
while IFS= read -r line; do
  case "$line" in
    GIT_SHA=*) GIT_SHA="${line#GIT_SHA=}" ;;
    *) printf '%s\n' "$line" >> zipzip-be.env.tmp ;;
  esac
done
mv zipzip-be.env.tmp zipzip-be.env

curl -fsSL "$REPO_RAW/$GIT_SHA/deploy/docker-compose.yml" -o docker-compose.yml
curl -fsSL "$REPO_RAW/$GIT_SHA/deploy/nginx/conf.d/api.conf" -o nginx/conf.d/api.conf

# 롤백 대비: pull하기 전에 지금 떠 있는(=정상 동작 중이었을) 컨테이너가 실제로 쓰던
# 이미지 ID를 기록해둔다. 최초 배포라 컨테이너가 없으면 빈 값으로 남는다.
PREV_IMAGE_ID=$(docker inspect --format='{{.Image}}' zipzip-be 2>/dev/null || true)

docker compose pull api
docker compose up -d api

# 헬스체크: api는 호스트에 포트를 안 열어서(expose만) 호스트에서 직접 접근할 수 없다.
# 실제 트래픽이 지나가는 것과 동일한 경로(nginx 컨테이너 -> 내부망 -> api:8080)로 확인해서
# 프록시 설정과 무관하게 앱이 실제로 응답하는지를 본다. spring-boot-starter-actuator가
# 이미 의존성에 있어 /actuator/health는 별도 설정 없이 기본 노출된다.
# 최대 60초(4초 x 15회) 기다려도 안 뜨면 실패로 간주한다.
healthy=0
for _ in $(seq 1 15); do
  if docker compose exec -T nginx wget -qO- --timeout=3 http://api:8080/actuator/health 2>/dev/null \
       | grep -q '"status":"UP"'; then
    healthy=1
    break
  fi
  sleep 4
done

if [ "$healthy" -ne 1 ]; then
  echo "::error:: new api container failed health check within 60s." >&2
  if [ -n "$PREV_IMAGE_ID" ]; then
    echo "rolling back zipzip-be to previous image ($PREV_IMAGE_ID)" >&2
    # pull이 :latest 태그를 새 이미지로 옮겨버렸으므로, 로컬에 남아있는 이전 이미지를
    # 다시 :latest로 되돌려 태깅한 뒤(재pull 없이) 그 이미지로 컨테이너를 재생성한다.
    DOCKERHUB_IMAGE=$(grep '^DOCKERHUB_IMAGE=' .env | cut -d= -f2)
    docker tag "$PREV_IMAGE_ID" "${DOCKERHUB_IMAGE}:latest"
    docker compose up -d api
  else
    echo "no previous image recorded (first deploy?) - nothing to roll back to." >&2
  fi
  exit 1
fi

if docker compose exec -T nginx nginx -t; then
  docker compose exec -T nginx nginx -s reload
else
  echo "nginx config test failed — not reloading. Check nginx/conf.d/*.conf on server." >&2
fi
```

nginx는 `nginx -t`(문법 검증)를 통과했을 때만 reload합니다 — 잘못된 설정으로 nginx가 죽는 걸 방지합니다.
헬스체크가 실패하면(설정 누락, DB 접속 실패, 마이그레이션 충돌 등 원인 불문) 이전 이미지로 자동 롤백하고
스크립트가 `exit 1`로 끝나 SSH 커맨드가 실패 처리되므로, GitHub Actions에서 그 배포가 실패했다는 게
빨간불로 명확히 남는다 — 단, 서비스 자체는 롤백된 이전 버전으로 계속 정상 동작한다.
certbot은 이 흐름에서 건드리지 않고, 인증서 갱신 루프만 별도로 계속 돕니다.

### dev 파이프라인도 동일한 방식

`cd-dev.yml` → `~/zipzip-deploy-dev.sh`도 완전히 같은 패턴(헬스체크+롤백 포함)입니다. 다만 nginx는 prod가
쓰는 것을 그대로 공유하므로, `dev-api.conf`는 `~/zipzip-deploy-dev`가 아니라
**`~/zipzip-deploy/nginx/conf.d/`에 씀**니다. 헬스체크도 같은 이유로 `~/zipzip-deploy`로 돌아와
`docker compose exec -T nginx`로 `http://api-dev:8080/actuator/health`를 확인합니다.

## 인증서 최초 발급 (1회성, 이미 완료됨)

```bash
docker compose up -d api nginx   # nginx는 80만으로 부트스트랩(HTTP-only 설정)
docker compose run --rm --entrypoint certbot certbot certonly \
  --webroot -w /var/www/certbot -d api.zipzip.site \
  --email <담당자 이메일> --agree-tos --non-interactive
# 이후 nginx/conf.d/api.conf를 443 포함 최종본으로 교체하고
docker compose exec nginx nginx -s reload
docker compose up -d certbot
```
