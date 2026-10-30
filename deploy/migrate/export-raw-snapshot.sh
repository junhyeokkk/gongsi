#!/usr/bin/env bash
# 로컬 docker Postgres 에서 raw_snapshot 하루치를 CSV 로 뽑는다 (id 제외 - RDS 에서 새로 발급)
# 사용: deploy/migrate/export-raw-snapshot.sh 2026-10-30 > raw_snapshot_2026-10-30.csv
set -euo pipefail

DAY="${1:?날짜(yyyy-MM-dd)가 필요하다}"

docker compose exec -T postgres psql -U gongsi -d gongsi -v ON_ERROR_STOP=1 -c "\copy (
  SELECT fetched_on, product_type, fin_grp_no, page_no, payload, fetched_at
  FROM raw_snapshot WHERE fetched_on = '${DAY}'
  ORDER BY product_type, fin_grp_no, page_no
) TO STDOUT WITH (FORMAT csv, HEADER)"
