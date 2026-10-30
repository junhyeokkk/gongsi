#!/usr/bin/env bash
# 릴리스 하나를 S3 에서 받아 설치하고 재시작한다. SSM Run Command 가 root 로 실행
# 사용: deploy.sh s3://<버킷>/releases/<sha>
set -euo pipefail

RELEASE="${1:?S3 릴리스 경로가 필요하다}"
APP_DIR=/opt/gongsi
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

aws s3 cp --recursive "$RELEASE" "$STAGE"

id gongsi >/dev/null 2>&1 || useradd --system --home-dir "$APP_DIR" --shell /sbin/nologin gongsi
install -d -o gongsi -g gongsi "$APP_DIR"
install -m 0755 "$STAGE/run.sh" "$APP_DIR/run.sh"
install -m 0644 "$STAGE/gongsi.service" /etc/systemd/system/gongsi.service

# 직전 jar 를 남겨 롤백할 수 있게 한다
[ -f "$APP_DIR/app.jar" ] && cp "$APP_DIR/app.jar" "$APP_DIR/app.jar.prev"
install -m 0644 -o gongsi -g gongsi "$STAGE/app.jar" "$APP_DIR/app.jar"

systemctl daemon-reload
systemctl enable gongsi >/dev/null
systemctl restart gongsi

# 기동 확인 - Flyway·컨텍스트가 뜨면 api-docs 가 응답한다
for _ in $(seq 1 30); do
  if curl -fsS -o /dev/null http://127.0.0.1:8080/v3/api-docs; then
    echo "배포 완료: $RELEASE"
    exit 0
  fi
  sleep 5
done

echo "기동 확인 실패 - 최근 로그:" >&2
journalctl -u gongsi -n 80 --no-pager >&2
exit 1
