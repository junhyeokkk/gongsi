# 정기예금 (DEP)

API `depositProductsSearch` · 테이블 product + product_option · 2026-10-30 상품 436 / 옵션 2,818

## base → product

```json
{
  "dcls_month": "202610", "fin_co_no": "0010001", "fin_prdt_cd": "WR0001B",
  "kor_co_nm": "우리은행", "fin_prdt_nm": "WON플러스예금",
  "join_way": "인터넷,스마트폰,전화(텔레뱅킹)", "join_deny": "1", "join_member": "실명의 개인",
  "mtrt_int": "만기 후\n- 1개월이내 : 만기시점약정이율×50%…", "spcl_cnd": "해당사항 없음",
  "etc_note": "- 가입기간: 1~36개월…", "max_limit": null,
  "dcls_strt_day": "20261020", "dcls_end_day": null, "fin_co_subm_day": "202610201420"
}
```

공통 필드는 [README](README.md#product-공통-필드-예금적금주담대전세). 예·적금 전용 필드:

| 원문 | product 컬럼 | 비고 |
| --- | --- | --- |
| `max_limit` | `max_limit` | 최고 한도(원). 398/435 가 null 이라 검색 조건으로 쓰지 않음 |
| `spcl_cnd` | `special_condition` | 우대조건 문장 |
| `mtrt_int` | `maturity_interest_note` | 만기 후 이율 문장 |

## option → product_option

```json
{"fin_co_no": "0010001", "fin_prdt_cd": "WR0001B",
 "intr_rate_type": "S", "intr_rate_type_nm": "단리", "save_trm": "1", "intr_rate": 2.7, "intr_rate2": 2.7}
```

| 원문 | product_option 컬럼 | 비고 |
| --- | --- | --- |
| `save_trm` `"1"` | `save_term` 1 | 개월. 원문이 문자열 → 정수, 숫자가 아니면 TYPE_MISMATCH |
| `intr_rate_type` | `rate_type` | S 단리 / M 복리, 그 외 UNKNOWN_ENUM |
| `intr_rate` | `base_rate` | 기본금리. null 0/2,816 |
| `intr_rate2` | `max_rate` | 우대 포함 최고금리 |
| — | `option_key` | `기간\|금리유형` → `1\|S` |

## 모양이 테이블을 정한 부분

- 한 상품이 "가입기간 × 단리/복리" 조합마다 금리를 하나씩 가진다 → 상품:옵션 1:N, 조합이 옵션키
- (save_trm + intr_rate_type) 이 상품 안에서 2,816/2,816 유일 — 옵션키로 충분
- 금리가 기본·최고 둘이라 변동 감지도 `base_rate`, `max_rate` 두 필드를 본다
