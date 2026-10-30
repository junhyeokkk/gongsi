#!/usr/bin/env bash
# SSM Parameter Store 의 /gongsi/* 를 환경변수로 올린 뒤 앱을 실행한다
# 인자는 그대로 앱에 넘긴다 - systemd 는 스케줄 켜기, 수동 실행은 Job 인자
set -euo pipefail

PREFIX="${SSM_PREFIX:-/gongsi}"

while IFS=$'\t' read -r name value; do
  export "${name##*/}=${value}"
done < <(aws ssm get-parameters-by-path --path "$PREFIX" --with-decryption \
  --query 'Parameters[].[Name,Value]' --output text)

: "${FINLIFE_API_KEY:?SSM ${PREFIX}/FINLIFE_API_KEY 없음}"
: "${DB_HOST:?SSM ${PREFIX}/DB_HOST 없음}"
: "${DB_PASS:?SSM ${PREFIX}/DB_PASS 없음}"
export DB_PORT="${DB_PORT:-5432}"

# t4g.micro(1GB) 에 맞춘 힙
exec java -Xms128m -Xmx384m -Duser.timezone=Asia/Seoul -jar /opt/gongsi/app.jar "$@"
