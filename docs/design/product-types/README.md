# 상품군별 원문 모양과 테이블 매핑

상품군마다 금감원 원문이 실제로 어떻게 생겼고, 각 필드가 어느 컬럼으로 가는지. 상품군 하나 = 문서 하나.
설계 이유의 전체 그림은 [../data-model.md](../data-model.md). 예시는 `survey/raw/*_20261028.json` 첫 레코드(긴 문장은 `…` 로 줄임).

| 문서 | 상품군 | 테이블 |
| --- | --- | --- |
| [deposit.md](deposit.md) | 정기예금 DEP | product · product_option |
| [saving.md](saving.md) | 적금 SAV | product · product_option |
| [mortgage-loan.md](mortgage-loan.md) | 주택담보대출 MORT | product · product_option |
| [rent-loan.md](rent-loan.md) | 전세자금대출 RENT | product · product_option |
| [credit-loan.md](credit-loan.md) | 개인신용대출 CRED | credit_loan_product · credit_loan_option |

## 공통 응답 구조

```json
{"result": {
  "prdt_div": "D", "total_count": 40, "max_page_no": 1, "now_page_no": 1, "err_cd": "000",
  "baseList":   [ {상품 1건}, … ],
  "optionList": [ {금리 조건 1건}, … ]
}}
```

- 한 상품(base)에 옵션 여러 건. 둘은 `fin_co_no + fin_prdt_cd` 로만 이어진다 — 옵션에 별도 id 가 없다
- 모든 레코드에 `dcls_month`(공시월)가 붙는다. 옵션의 것은 base 와 같아 저장하지 않는다
- `*_nm` 필드(`intr_rate_type_nm: "단리"` 등)는 코드의 한글 이름이다. 코드만 저장하고 이름은 표시 단계에서 붙인다

## product 공통 필드 (예금·적금·주담대·전세)

| 원문 | product 컬럼 | 비고 |
| --- | --- | --- |
| `fin_co_no` / `fin_prdt_cd` | `inst_code` / `product_code` | + `product_type` 이 자연키 |
| `kor_co_nm` / `fin_prdt_nm` | `inst_name` / `product_name` | 필수 |
| `join_way`, `join_member`, `etc_note` | 같은 이름 | 없으면 null |
| `join_deny` `"1"` | `join_deny` 1 | 1 제한없음 / 2 서민전용 / 3 일부제한 |
| `dcls_month` | `disclosure_month` | `yyyyMM`, 필수 |
| `dcls_strt_day` / `dcls_end_day` | `disclosure_start_date` / `_end_date` | `yyyyMMdd`. `"99991231"` → null |
| `fin_co_subm_day` | `submitted_at` | `yyyyMMddHHmm` |
| (요청한 권역) | `fin_grp_no` | 원문 레코드에 없다. 그 페이지를 받은 `raw_snapshot.fin_grp_no` 를 옮겨 적는다. 020000 은행 / 030300 저축은행 |

상품군 전용 필드는 각 문서에 있다.

## 한눈에

| | 예금 | 적금 | 주담대 | 전세 | 신용대출 |
| --- | --- | --- | --- | --- | --- |
| 상품 테이블 | product | product | product | product | credit_loan_product |
| 상품 전용 필드 | 예·적금 3개 | 예·적금 3개 | 대출 4개 | 대출 4개 | `cb_name` |
| 옵션을 가르는 것 | 기간·금리유형 | + 적립방식 | 담보·상환·금리유형 | 상환·금리유형 | 금리종류 |
| 옵션의 금리 | 기본·최고 | 기본·최고 | 최저·최고·평균 | 최저·최고·평균 | 신용구간 8개 + 평균 |
| 2026-10-30 상품/옵션 | 436 / 2,818 | 337 / 1,534 | 92 / 268 | 49 / 96 | 미적재 |
