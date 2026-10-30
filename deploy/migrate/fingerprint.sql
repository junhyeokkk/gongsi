-- raw_snapshot 하루치의 행 수와 내용 지문 - 로컬과 RDS 결과가 같아야 이전 성공
-- 사용: psql -v d=2026-10-30 -f fingerprint.sql
SELECT count(*) AS rows,
       md5(string_agg(concat_ws('|', product_type, fin_grp_no, page_no, md5(payload::text), extract(epoch FROM fetched_at)),
                      ',' ORDER BY product_type, fin_grp_no, page_no)) AS fingerprint
FROM raw_snapshot
WHERE fetched_on = :'d';
