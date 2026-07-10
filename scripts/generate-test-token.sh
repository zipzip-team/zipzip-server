#!/bin/bash
# 로컬 개발 전용: Apple 로그인 없이, 로컬 config/application-secret.yml의 jwt.access-secret으로
# 직접 서명한 access token을 만든다. 실제 이 값으로 서명·검증하므로 로컬 서버가 어떻게(gradle bootRun,
# IntelliJ 등) 떠 있든 항상 통한다. 운영 환경에는 이 스크립트도, 이 방식도 절대 쓰지 않는다.
#
# 사용법:
#   ./scripts/generate-test-token.sh [app_user_id] [expires_in_seconds]
#   기본 app_user_id: 00000000-0000-0000-0000-000000000001 (README에 시드 SQL로 심어둔 테스트 유저)
#   기본 유효기간: 365일

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="$SCRIPT_DIR/../src/main/resources/config/application-secret.yml"

if [ ! -f "$CONFIG_FILE" ]; then
  echo "설정 파일을 찾을 수 없습니다: $CONFIG_FILE" >&2
  echo "src/main/resources/config는 private 서브모듈입니다. git submodule update --init 했는지 확인하세요." >&2
  exit 1
fi

SUB="${1:-00000000-0000-0000-0000-000000000001}"
EXPIRES_IN="${2:-31536000}"

ISSUER=$(awk '/^jwt:/{f=1;next} f&&/^[^ ]/{f=0} f&&/issuer:/{print $2}' "$CONFIG_FILE")
SECRET=$(awk '/^jwt:/{f=1;next} f&&/^[^ ]/{f=0} f&&/access-secret:/{print $2}' "$CONFIG_FILE")

if [ -z "$ISSUER" ] || [ -z "$SECRET" ]; then
  echo "jwt.issuer / jwt.access-secret을 $CONFIG_FILE 에서 읽지 못했습니다." >&2
  exit 1
fi

b64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

HEADER=$(printf '{"alg":"HS256","typ":"JWT"}' | b64url)
IAT=$(date +%s)
EXP=$((IAT + EXPIRES_IN))
PAYLOAD=$(printf '{"iss":"%s","sub":"%s","token_type":"access","iat":%s,"exp":%s}' \
  "$ISSUER" "$SUB" "$IAT" "$EXP" | b64url)
SIGNING_INPUT="${HEADER}.${PAYLOAD}"
SIGNATURE=$(printf '%s' "$SIGNING_INPUT" | openssl dgst -sha256 -hmac "$SECRET" -binary | b64url)

echo "${SIGNING_INPUT}.${SIGNATURE}"
