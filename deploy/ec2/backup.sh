#!/usr/bin/env bash
# raw_snapshot 하루치를 csv.gz 와 지문으로 S3 에 남긴다 - RDS 자동 백업(1일)과 별개인 날짜별 영구 보관
# 사용: backup.sh [yyyy-MM-dd]  (기본 오늘 KST). 복구는 deploy/migrate/import.sql
set -euo pipefail

DAY="${1:-$(TZ=Asia/Seoul date +%F)}"
[[ "$DAY" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || { echo "날짜 형식 오류: $DAY" >&2; exit 2; }
BUCKET="${BACKUP_BUCKET:?BACKUP_BUCKET 이 필요하다}"
HERE="$(dirname "$0")"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

. "$HERE/ssm-env.sh"
export PGHOST="$DB_HOST" PGPORT="$DB_PORT" PGDATABASE="${DB_NAME:-gongsi}" PGUSER="${DB_USER:-gongsi}" \
       PGPASSWORD="$DB_PASS" PGSSLMODE="${PGSSLMODE:-require}"

psql -v ON_ERROR_STOP=1 -c "\copy (
  SELECT fetched_on, product_type, fin_grp_no, page_no, payload, fetched_at
  FROM raw_snapshot WHERE fetched_on = '${DAY}'
  ORDER BY product_type, fin_grp_no, page_no
) TO STDOUT WITH (FORMAT csv, HEADER)" | gzip -9 > "$OUT/$DAY.csv.gz"

# 수집이 없던 날을 빈 파일로 덮지 않도록 실패 처리 - journal 에 남는다
ROWS=$(( $(gzip -dc "$OUT/$DAY.csv.gz" | wc -l) - 1 ))
[ "$ROWS" -gt 0 ] || { echo "$DAY raw_snapshot 0행 - 백업 안 함" >&2; exit 1; }

psql -At -v d="$DAY" -f "${FP_SQL:-$HERE/fingerprint.sql}" > "$OUT/$DAY.fingerprint"

aws s3 cp --only-show-errors "$OUT/$DAY.csv.gz" "s3://$BUCKET/raw_snapshot/$DAY.csv.gz"
aws s3 cp --only-show-errors "$OUT/$DAY.fingerprint" "s3://$BUCKET/raw_snapshot/$DAY.fingerprint"
echo "백업 완료: $DAY rows=$ROWS fingerprint=$(cat "$OUT/$DAY.fingerprint")"
