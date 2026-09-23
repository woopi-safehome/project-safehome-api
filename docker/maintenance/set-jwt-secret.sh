#!/usr/bin/env bash
# 개발 서버의 API 환경 파일에 JWT_SECRET 을 넣고 API 컨테이너를 다시 띄운다. 서버에서 사람이 한 번 돌린다.
#
# 왜 필요한가: application-auth.yml 은 JWT_SECRET 이 없으면 저장소에 적힌 기본값으로 토큰에 서명한다.
# 저장소가 공개라 그 기본값은 누구나 알고, 그러면 누구나 아무 회원의 토큰을 만들어 낼 수 있다.
#
# 하는 일
#   1. 환경 파일을 백업한다 (같은 폴더, 시각이 붙은 이름)
#   2. 임의의 값을 만들어 JWT_SECRET 한 줄을 추가한다 — 값은 화면에 찍지 않는다
#   3. API 컨테이너만 새로 만든다 (이미지는 그대로, DB·캐시는 건드리지 않는다)
#   4. 헬스 체크가 healthy 가 될 때까지 기다리고, 컨테이너가 값을 받았는지 확인한다
#
# 대가: 키가 바뀌므로 **기존 로그인 토큰은 모두 무효**가 된다. 다시 로그인하면 된다. 저장된 데이터는 그대로다.
#
# 사용
#   bash set-jwt-secret.sh            # 없을 때만 넣는다. 이미 있으면 아무것도 하지 않는다
#   bash set-jwt-secret.sh --rotate   # 이미 있어도 새 값으로 바꾼다 (키가 샜다고 의심될 때)
set -euo pipefail

ENV_FILE=/home/woopi/project/safehome/env/.env_api
DEPLOY_PATH=/home/woopi/project/safehome/api
CONTAINER=safehome-api-dev

ROTATE=false
[ "${1:-}" = "--rotate" ] && ROTATE=true

[ -f "$ENV_FILE" ] || { echo "환경 파일이 없다: $ENV_FILE"; exit 1; }
[ -f "$DEPLOY_PATH/docker-compose.yml" ] || { echo "배포 구성이 없다: $DEPLOY_PATH/docker-compose.yml"; exit 1; }
command -v openssl >/dev/null || { echo "openssl 이 필요하다: sudo apt-get install -y openssl"; exit 1; }

if grep -q '^JWT_SECRET=' "$ENV_FILE" && [ "$ROTATE" = false ]; then
  echo "JWT_SECRET 이 이미 있다. 바꾸지 않는다. (새 값으로 바꾸려면 --rotate)"
  exit 0
fi

# 1. 백업 — 비밀값이 들어 있으므로 권한을 좁힌다
BACKUP="$ENV_FILE.bak.$(date +%Y%m%d-%H%M%S)"
cp -p "$ENV_FILE" "$BACKUP"
chmod 600 "$BACKUP"
echo "백업: $BACKUP"

# 2. 값 생성 — 16진수라 env 파일에서 따옴표·특수문자 문제가 없다. 96자(384비트)
SECRET=$(openssl rand -hex 48)
TMP=$(mktemp)
grep -v '^JWT_SECRET=' "$ENV_FILE" > "$TMP" || true
# 마지막 줄에 줄바꿈이 없으면 새 줄이 앞 줄에 붙는다
[ -s "$TMP" ] && [ "$(tail -c1 "$TMP")" != "" ] && echo >> "$TMP"
echo "JWT_SECRET=$SECRET" >> "$TMP"
cat "$TMP" > "$ENV_FILE"   # 원래 파일의 소유자·권한을 유지하려고 덮어쓴다
rm -f "$TMP"
unset SECRET
chmod 600 "$ENV_FILE"
echo "JWT_SECRET 을 넣었다 (값은 출력하지 않는다)"

# 3. API 컨테이너만 새로 만든다 — env_file 은 컨테이너를 만들 때 읽히므로 재시작(restart)으로는 반영되지 않는다
cd "$DEPLOY_PATH"
docker compose --env-file "$ENV_FILE" up -d --no-deps --force-recreate safehome-api

# 4. 기동 확인 — 배포 워크플로와 같은 방식
STATUS=starting
for _ in $(seq 1 36); do
  STATUS=$(docker inspect -f '{{.State.Health.Status}}' "$CONTAINER" 2>/dev/null || echo missing)
  case "$STATUS" in
    healthy)  break ;;
    starting) sleep 5 ;;
    *)        break ;;
  esac
done
if [ "$STATUS" != "healthy" ]; then
  echo "기동 실패 — 상태: $STATUS. 되돌리려면: cp -p $BACKUP $ENV_FILE 후 이 폴더에서 같은 compose 명령"
  docker logs --tail 80 "$CONTAINER" 2>&1 || true
  exit 1
fi
echo "기동 확인: healthy"

# 컨테이너가 값을 실제로 받았는지 — 길이만 본다
LEN=$(docker exec "$CONTAINER" sh -c 'printf %s "${JWT_SECRET:-}" | wc -c' | tr -d ' ')
if [ "$LEN" -ge 64 ]; then
  echo "컨테이너 안의 JWT_SECRET 길이: $LEN — 적용됨"
else
  echo "컨테이너 안에 JWT_SECRET 이 없거나 짧다 (길이 $LEN). env_file 경로를 확인하라"
  exit 1
fi

echo "완료. 기존 로그인은 풀렸다 — 다시 로그인하면 된다."
