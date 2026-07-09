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

`APPLE_PRIVATE_KEY(_DEV)`는 서버 env 파일과 SSH heredoc 전달 경로가 줄 단위로 동작하므로 raw multiline
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

docker compose pull api
docker compose up -d api

if docker compose exec -T nginx nginx -t; then
  docker compose exec -T nginx nginx -s reload
else
  echo "nginx config test failed — not reloading." >&2
fi
```

nginx는 `nginx -t`(문법 검증)를 통과했을 때만 reload합니다 — 잘못된 설정으로 nginx가 죽는 걸 방지합니다.
certbot은 이 흐름에서 건드리지 않고, 인증서 갱신 루프만 별도로 계속 돕니다.

### dev 파이프라인도 동일한 방식

`cd-dev.yml` → `~/zipzip-deploy-dev.sh`도 완전히 같은 패턴입니다. 다만 nginx는 prod가 쓰는 것을 그대로
공유하므로, `dev-api.conf`는 `~/zipzip-deploy-dev`가 아니라 **`~/zipzip-deploy/nginx/conf.d/`에 씀**니다.

현재 서버 배포 스크립트는 `docker compose up -d` 이후 애플리케이션 health check와 rollback까지 수행하지는
않습니다. CD workflow는 GitHub Secrets 값이 비어 있으면 SSH 전에 실패시키지만, 잘못된 값으로 인한 런타임
기동 실패까지 자동 복구하려면 서버의 `~/zipzip-deploy.sh`, `~/zipzip-deploy-dev.sh`에 health check와 rollback
절차를 추가해야 합니다.

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
