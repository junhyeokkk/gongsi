# 주택담보대출 (MORT)

API `mortgageLoanProductsSearch` · 테이블 product + product_option · 2026-10-30 상품 92 / 옵션 268

## base → product

```json
{
  "dcls_month": "202610", "fin_co_no": "0010001", "fin_prdt_cd": "1054",
  "kor_co_nm": "우리은행", "fin_prdt_nm": "우리아파트론", "join_way": "영업점,모집인",
  "loan_inci_expn": "- 인지세 : 해당세액의 50%…", "erly_rpay_fee": "- 고정금리 : 중도상환대출금×0.71%…",
  "dly_rate": "- 적용금리+ 3%…", "loan_lmt": "LTV 70%",
  "dcls_strt_day": "20261020", "dcls_end_day": "20271019", "fin_co_subm_day": "202610161437"
}
```

공통 필드는 [README](README.md#product-공통-필드-예금적금주담대전세). 대출 전용 필드:

| 원문 | product 컬럼 | 비고 |
| --- | --- | --- |
| `loan_lmt` | `loan_limit_note` | 대출한도. "LTV 70%" 같은 문장 |
| `erly_rpay_fee` | `early_repayment_fee_note` | 중도상환수수료 |
| `dly_rate` | `delinquency_rate_note` | 연체이자율 |
| `loan_inci_expn` | `incidental_expense_note` | 부대비용 |

대출 전용 필드는 모두 숫자가 아니라 설명 문장이다. 파싱하지 않고 TEXT 로 그대로 둔다.
예·적금 전용 컬럼(`max_limit` 등)은 null 로 남는다.

## option → product_option

```json
{"fin_co_no": "0010001", "fin_prdt_cd": "1054",
 "mrtg_type": "A", "mrtg_type_nm": "아파트", "rpay_type": "D", "rpay_type_nm": "분할상환방식",
 "lend_rate_type": "F", "lend_rate_type_nm": "고정금리",
 "lend_rate_min": 5.39, "lend_rate_max": 6.59, "lend_rate_avg": 6.03}
```

| 원문 | product_option 컬럼 | 비고 |
| --- | --- | --- |
| `mrtg_type` | `mortgage_type` | A 아파트 / E 아파트외 |
| `rpay_type` | `repay_type` | D 분할상환 / S 만기일시 |
| `lend_rate_type` | `lend_rate_type` | F 고정 / C 변동 |
| `lend_rate_min` / `lend_rate_max` | `rate_min` / `rate_max` | 금리 범위. 변동 감지 대상 |
| `lend_rate_avg` | `rate_avg` | 전월 취급 평균. 170/268 null → 변동 감지 제외 |
| — | `option_key` | `담보\|상환\|금리유형` → `A\|D\|F` |

## 모양이 테이블을 정한 부분

- 옵션 = "담보 × 상환 × 금리유형" 조합, (mrtg + rpay + lend_rate_type) 268/268 유일 → 옵션키
- 금리가 한 값이 아니라 최저·최고·평균 범위 → 예·적금의 `base_rate/max_rate` 와 따로 `rate_min/max/avg` 를 둔다
- 예·적금과 쓰는 옵션 컬럼은 겹치지 않지만 "상품:옵션 1:N, 옵션키로 날마다 1:1 비교" 구조가 같아 한 테이블에 nullable 로 둔다
