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
- `~/zipzip-deploy/zipzip-be.env`: 프로덕션 DB 접속정보(`SPRING_DATASOURCE_*`). git/이미지에 절대 포함하지 않음.
  CD가 배포할 때마다 GitHub Secrets 값으로 덮어씀(아래 배포 흐름 참고).
- `~/zipzip-deploy/certbot/conf`: 발급된 인증서.

## 배포 흐름

CD(`​.github/workflows/cd.yml`)가 이미지를 Docker Hub에 push한 뒤, 서버의 CD 전용 SSH 배포키로 접속합니다.
이 키는 `authorized_keys`에 forced command로 제한되어 있어 실제로는 클라이언트가 보낸 명령과 무관하게
`~/zipzip-deploy.sh`만 실행됩니다. DB 접속정보는 GitHub Secrets(`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`)에
개별 등록해두고, forced command라 인자로 못 넘기니 **stdin으로 흘려보내** 스크립트가 그대로
`zipzip-be.env`에 기록한 뒤 배포합니다:

```bash
#!/bin/bash
set -e
cd "$HOME/zipzip-deploy"
cat > zipzip-be.env      # stdin으로 받은 내용을 그대로 기록
docker compose pull api
docker compose up -d api
```

nginx/certbot은 이미지가 자주 바뀌지 않으므로 배포 때마다 재기동하지 않고, 설정을 바꿀 때만 수동으로
`docker compose up -d nginx` 등으로 반영합니다.

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
