-- 상품이 속한 권역 - 020000 은행 / 030300 저축은행
-- 원문 레코드에 없고 요청한 권역으로만 알 수 있어 정규화 때 raw_snapshot.fin_grp_no 를 옮겨 적는다
-- 기존 행은 다음 정규화에서 채워지므로 nullable
ALTER TABLE product ADD COLUMN fin_grp_no VARCHAR(6);
ALTER TABLE credit_loan_product ADD COLUMN fin_grp_no VARCHAR(6);
