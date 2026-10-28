# 2026-10-28 첫 전체 수집

`survey/fetch.py` 첫 실행. 서비스 5 × 권역 2(은행·저축은행), 15호출, 실패 0, raw 2.4MB.

## 요약

| service | group | total | pages | base | option |
| --- | --- | --- | --- | --- | --- |
| depositProductsSearch | 020000 | 40 | 1 | 40 | 164 |
| depositProductsSearch | 030300 | 395 | 4 | 395 | 2652 |
| savingProductsSearch | 020000 | 60 | 1 | 60 | 181 |
| savingProductsSearch | 030300 | 277 | 3 | 277 | 1353 |
| mortgageLoanProductsSearch | 020000 | 36 | 1 | 36 | 111 |
| mortgageLoanProductsSearch | 030300 | 56 | 1 | 56 | 157 |
| rentHouseLoanProductsSearch | 020000 | 41 | 1 | 41 | 80 |
| rentHouseLoanProductsSearch | 030300 | 8 | 1 | 8 | 16 |
| creditLoanProductsSearch | 020000 | 44 | 1 | 44 | 139 |
| creditLoanProductsSearch | 030300 | 36 | 1 | 36 | 107 |

상품 ~1,000 / 옵션 ~5,000. 저축은행이 은행의 약 10배. 하루 호출 15회 (상한 10,000).

## 검증 결과

| 항목 | 결과 |
| --- | --- |
| 자연키 `fin_co_no + fin_prdt_cd` 유니크 | 예금 435/435, 적금 337/337, 주담대 92/92, 전세 49/49 통과. **신용대출 80건 중 79 — 1건 중복** (`0010016/WR0002F`: 마이너스한도대출·카드론 두 행, cb_name KCB/NICE) |
| 옵션 식별키 유니크 | 예금 (save_trm + intr_rate_type) 2,816/2,816, 주담대 (mrtg_type + rpay_type + lend_rate_type) 268/268 통과 |
| `dcls_month` | 전부 202610 |
| `dcls_end_day` | null 925 / "99991231" 26 / 실제 날짜 42 |
| `intr_rate` null | 0 / 2,816 |
| `lend_rate_avg` null | 170 / 268 (주담대) |
| `max_limit` null | 398 / 435 (예금) |
| 적금 `rsrv_type` | S 1,176 / F 358 |
| 적금 `intr_rate_type` | S 1,175 / M 359 |
| 금리 필드 타입 | JSON 숫자. `save_trm`만 문자열 |
| 신용대출 baseList | `cb_name` 있음, `join_member`·`etc_note` 없음 — 다른 서비스와 필드 구성이 다름 |
| 신용대출 optionList | `crdt_prdt_type` 1:173 / 2:65 / 3:8, `crdt_lend_rate_type` A:78 / B:70 / C:69 / D:29 |

## 설계에 미치는 영향

- 신용대출은 자연키에 `crdt_prdt_type`이 필요하고 필드 구성도 달라 **별도 테이블 또는 범위 제외** — 03 합의 항목
- `intr_rate`는 NOT NULL 가능, `lend_rate_avg`는 nullable 필수이며 변동 감지 대상에서 제외
- `max_limit`은 대부분 null이라 검색 조건 부적합
- `dcls_end_day`의 "99991231"은 적재 시 null로 정규화
- 호출 제한은 설계 고려 대상 아님

## 남은 것

- 11/18~25 매일 실행해 변동 건수 관측 (20일 전후)
- 여신전문(030200)·보험(050000)·금융투자(060000) 권역 대출 1회 호출 후 포함 여부 결정
