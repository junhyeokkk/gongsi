#!/usr/bin/env bash
# 최신 GitHub Release 를 받아 설치하고 재시작한다. gongsi-update.timer 가 root 로 10분마다 실행
# 처음 한 번: curl -fsSL https://github.com/junhyeokkk/gongsi/releases/latest/download/deploy.sh | sudo bash
set -euo pipefail

BASE="${RELEASE_BASE:-https://github.com/junhyeokkk/gongsi/releases/latest/download}"
APP_DIR=/opt/gongsi
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

fetch() { curl -fsSL --retry 3 -o "$STAGE/$1" "$BASE/$1"; }

fetch VERSION
NEW="$(cat "$STAGE/VERSION")"
# 같은 버전이거나 이미 실패한 버전이면 아무것도 하지 않는다
[ "$NEW" = "$(cat "$APP_DIR/VERSION" 2>/dev/null)" ] && exit 0
[ "$NEW" = "$(cat "$APP_DIR/FAILED_VERSION" 2>/dev/null)" ] && exit 0

for f in app.jar run.sh deploy.sh gongsi.service gongsi-update.service gongsi-update.timer; do fetch "$f"; done

id gongsi >/dev/null 2>&1 || useradd --system --home-dir "$APP_DIR" --shell /sbin/nologin gongsi
install -d -o gongsi -g gongsi "$APP_DIR"
install -m 0755 "$STAGE/run.sh" "$APP_DIR/run.sh"
install -m 0755 "$STAGE/deploy.sh" "$APP_DIR/deploy.sh"
install -m 0644 "$STAGE"/gongsi.service "$STAGE"/gongsi-update.service "$STAGE"/gongsi-update.timer /etc/systemd/system/

[ -f "$APP_DIR/app.jar" ] && cp "$APP_DIR/app.jar" "$APP_DIR/app.jar.prev"
install -m 0644 -o gongsi -g gongsi "$STAGE/app.jar" "$APP_DIR/app.jar"

systemctl daemon-reload
systemctl enable --now gongsi-update.timer >/dev/null
systemctl enable gongsi >/dev/null
systemctl restart gongsi

# 기동 확인 - Flyway·컨텍스트가 뜨면 api-docs 가 응답한다
for _ in $(seq 1 36); do
  if curl -fsS -o /dev/null http://127.0.0.1:8080/v3/api-docs; then
    echo "$NEW" > "$APP_DIR/VERSION"
    rm -f "$APP_DIR/FAILED_VERSION"
    echo "배포 완료: $NEW"
    exit 0
  fi
  sleep 5
done

# 실패하면 직전 jar 로 되돌리고 이 버전은 다시 시도하지 않는다
echo "기동 확인 실패: $NEW - 최근 로그:" >&2
journalctl -u gongsi -n 80 --no-pager >&2
echo "$NEW" > "$APP_DIR/FAILED_VERSION"
if [ -f "$APP_DIR/app.jar.prev" ]; then
  install -m 0644 -o gongsi -g gongsi "$APP_DIR/app.jar.prev" "$APP_DIR/app.jar"
  systemctl restart gongsi
fi
exit 1
