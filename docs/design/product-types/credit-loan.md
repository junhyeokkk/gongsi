# 개인신용대출 (CRED)

API `creditLoanProductsSearch` · 테이블 **credit_loan_product + credit_loan_option** (별도) · 정규화 `CreditNormalizer`

## base → credit_loan_product

```json
{
  "dcls_month": "202610", "fin_co_no": "0010001", "fin_prdt_cd": "CR0001A",
  "crdt_prdt_type": "1", "crdt_prdt_type_nm": "일반신용대출",
  "kor_co_nm": "우리은행", "fin_prdt_nm": "협약금리 外 신용대출상품", "join_way": "영업점,인터넷,스마트폰",
  "cb_name": "KCB", "dcls_strt_day": "20261016", "dcls_end_day": "99991231", "fin_co_subm_day": "202610161557"
}
```

| 원문 | 비고 |
| --- | --- |
| `fin_co_no` / `fin_prdt_cd` / `crdt_prdt_type` | **셋이 자연키.** 1 일반 / 2 마이너스한도 / 3 카드론 |
| `crdt_prdt_type` 이 1·2·3 밖이면 | `UNKNOWN_ENUM` 으로 격리 |
| `kor_co_nm` / `fin_prdt_nm` / `join_way` | |
| `cb_name` | 신용평가사 KCB / NICE. 다른 상품군에 없음. 원문에 `kcb` 가 섞여 대문자로 맞춘다. `NICE,KCB` 같은 복수 표기도 있다 |
| (페이지 권역) | `fin_grp_no` — 원문에 없어 raw_snapshot 의 권역을 옮겨 적는다 |
| 공시월·공시기간·제출일 | `"99991231"` → null |
| — | `join_deny`·`join_member`·`etc_note`·대출 문장 필드가 원문에 없다 |

## option → credit_loan_option

```json
{"fin_co_no": "0010001", "fin_prdt_cd": "CR0001A", "crdt_prdt_type": "1",
 "crdt_lend_rate_type": "A", "crdt_lend_rate_type_nm": "대출금리",
 "crdt_grad_1": 6.03, "crdt_grad_4": 6.83, "crdt_grad_5": 7.2, "crdt_grad_6": 7.45,
 "crdt_grad_10": 7.5, "crdt_grad_11": 7.4, "crdt_grad_12": 9.21, "crdt_grad_13": 9.12, "crdt_grad_avg": 6.62}
```

| 원문 | credit_loan_option 컬럼 | 비고 |
| --- | --- | --- |
| `crdt_lend_rate_type` | `lend_rate_type` | A 대출금리 / B 기준금리 / C 가산금리 / D 가감조정금리 → 상품당 최대 4행. 옵션키. 밖이면 `UNKNOWN_ENUM` |
| `crdt_grad_1` … `crdt_grad_13` (8개) | `rate_grade_1` … `rate_grade_13` | 신용점수 구간별 금리. 구간 번호 1,4,5,6,10,11,12,13 고정 |
| `crdt_grad_avg` | `rate_avg` | |

## 모양이 테이블을 정한 부분

- 같은 상품코드가 일반·마이너스한도·카드론으로 나뉘어 나온다(`0010016/WR0002F`, 80건 중 1건 중복)
  → `product` 의 자연키(기관+상품코드+상품군)로는 구분할 수 없어 `crdt_prdt_type` 을 키에 넣은 별도 테이블
- base 필드 구성이 다르다 — `cb_name` 이 있고 가입대상·기타·대출 문장 필드가 없다
- 옵션 한 건에 금리가 8~9개 들어 있다 → product_option 의 "옵션 한 건 = 금리 한 쌍" 구조와 맞지 않는다
- 구간이 고정이라 행으로 펼치지 않고 가로 컬럼으로 둔다. "어제 4구간 vs 오늘 4구간"이 같은 행 안의 비교가 된다
- 금리 종류(A~D)가 옵션을 가르므로 `(credit_loan_product_id, lend_rate_type)` 이 옵션 유일키다

## 변동 감지

- 비교 필드: `rate_grade_*` 8개 + `rate_avg`. 평균은 원문 null 0건(2026-10-28·11-08~11-11)이라 넣었다. 구간 금리는 null 이 많다(10~13구간)
- `credit_rate_history.field` 는 컬럼 이름 그대로, change_event 는 `credit_loan_product_id`(+ `credit_loan_option_id`)로 낸다
- A~D 를 모두 비교한다. 기준금리(B)가 움직이면 대출금리(A)와 이벤트가 같이 생긴다 — 알림에서 거를 일
- 금리가 월 공시라 같은 달 안의 일 변동은 실측 0건이다(11-01 → 11-11)
