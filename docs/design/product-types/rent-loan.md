# 전세자금대출 (RENT)

API `rentHouseLoanProductsSearch` · 테이블 product + product_option · 2026-10-30 상품 49 / 옵션 96

## base → product

```json
{
  "dcls_month": "202610", "fin_co_no": "0010001", "fin_prdt_cd": "10561",
  "kor_co_nm": "우리은행", "fin_prdt_nm": "우리전세론(주택금융보증)", "join_way": "영업점,모집인",
  "loan_inci_expn": "- 인지세 : 해당세액의 50%…\n- 보증료", "erly_rpay_fee": "- 고정금리 : 중도상환대출금×0.76%…",
  "dly_rate": "- 적용금리+ 3%…", "loan_lmt": "최대4.44억원",
  "dcls_strt_day": "20261020", "dcls_end_day": "20271019", "fin_co_subm_day": "202610161437"
}
```

주담대와 같은 필드 구성이다. 공통 필드는 [README](README.md#product-공통-필드-예금적금주담대전세),
대출 전용 필드 4개는 [mortgage-loan.md](mortgage-loan.md#base--product).

## option → product_option

```json
{"fin_co_no": "0010001", "fin_prdt_cd": "10561",
 "rpay_type": "D", "rpay_type_nm": "분할상환방식", "lend_rate_type": "C", "lend_rate_type_nm": "변동금리",
 "lend_rate_min": 4.04, "lend_rate_max": 4.74, "lend_rate_avg": null}
```

| 원문 | product_option 컬럼 | 비고 |
| --- | --- | --- |
| — | `mortgage_type` | **원문에 없음** → null |
| `rpay_type` | `repay_type` | D 분할상환 / S 만기일시 |
| `lend_rate_type` | `lend_rate_type` | F 고정 / C 변동 |
| `lend_rate_min` / `lend_rate_max` | `rate_min` / `rate_max` | 변동 감지 대상 |
| `lend_rate_avg` | `rate_avg` | 예시처럼 null 이 흔하다 → 변동 감지 제외 |
| — | `option_key` | `상환\|금리유형` → `D\|C` |

## 모양이 테이블을 정한 부분

- 주담대 옵션에서 담보유형만 빠진 모양 → 같은 컬럼을 쓰고 `mortgage_type` 만 null
- 담보유형이 없으니 옵션키도 두 칸이다. 주담대처럼 빈 칸을 넣지 않는다
