-- export-raw-snapshot.sh 로 뽑은 CSV 를 표준입력으로 받아 raw_snapshot 에 넣는다
-- 같은 날짜가 이미 있으면 UNIQUE 위반으로 전체 롤백
-- 사용: psql -v ON_ERROR_STOP=1 -f import.sql < raw_snapshot_2026-10-30.csv
BEGIN;
\copy raw_snapshot (fetched_on, product_type, fin_grp_no, page_no, payload, fetched_at) FROM pstdin WITH (FORMAT csv, HEADER)
COMMIT;
