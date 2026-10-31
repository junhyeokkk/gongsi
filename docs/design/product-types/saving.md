# 적금 (SAV)

API `savingProductsSearch` · 테이블 product + product_option · 2026-10-30 상품 337 / 옵션 1,534

## base → product

```json
{
  "dcls_month": "202610", "fin_co_no": "0010001", "fin_prdt_cd": "WR0001F",
  "kor_co_nm": "우리은행", "fin_prdt_nm": "우리SUPER주거래적금",
  "join_way": "영업점,인터넷,스마트폰,전화(텔레뱅킹)", "join_deny": "1", "join_member": "실명의 개인",
  "mtrt_int": "만기 후\n- 1개월이내 : 만기시점약정이율×50%…",
  "spcl_cnd": "1.우리은행 입출식 계좌에서 각 항목별 실적 월 수가…", "etc_note": "1. 가입기간 : 1년/2년/3년…",
  "max_limit": null, "dcls_strt_day": "20261020", "dcls_end_day": null, "fin_co_subm_day": "202610201420"
}
```

예금과 같은 필드 구성이다. 공통 필드는 [README](README.md#product-공통-필드-예금적금주담대전세),
예·적금 전용 필드(`max_limit`, `spcl_cnd`, `mtrt_int`)는 [deposit.md](deposit.md#base--product).

## option → product_option

```json
{"fin_co_no": "0010001", "fin_prdt_cd": "WR0001F",
 "intr_rate_type": "S", "intr_rate_type_nm": "단리", "rsrv_type": "S", "rsrv_type_nm": "정액적립식",
 "save_trm": "12", "intr_rate": 2.45, "intr_rate2": 3.85}
```

| 원문 | product_option 컬럼 | 비고 |
| --- | --- | --- |
| `save_trm` `"12"` | `save_term` 12 | 개월. 문자열 → 정수 |
| `intr_rate_type` | `rate_type` | S 단리 1,175 / M 복리 359 |
| `rsrv_type` | `reserve_type` | **적금만.** S 정액적립 1,176 / F 자유적립 358 |
| `intr_rate` | `base_rate` | 기본금리 |
| `intr_rate2` | `max_rate` | 우대 포함 최고금리. 예시처럼 기본보다 크게 벌어지는 경우가 많다 |
| — | `option_key` | `기간\|금리유형\|적립방식` → `12\|S\|S` |

## 모양이 테이블을 정한 부분

- 예금 옵션에 적립방식(`rsrv_type`) 하나가 더 붙은 모양 → 예금과 같은 테이블, `reserve_type` 컬럼만 추가
- 같은 기간·금리유형에 정액·자유 적립이 함께 있을 수 있어 옵션키에 적립방식을 넣는다. 빼면 두 옵션이 한 행으로 덮인다
