# 개인신용대출 (CRED)

API `creditLoanProductsSearch` · 테이블 **credit_loan_product + credit_loan_option** (별도) · 정규화 미구현

## base → credit_loan_product

```json
{
  "dcls_month": "202610", "fin_co_no": "0010001", "fin_prdt_cd": "CR0001A",
  "crdt_prdt_type": "1", "crdt_prdt_type_nm": "일반신용대출",
  "kor_co_nm": "우리은행", "fin_prdt_nm": "협약금리 外 신용대출상품", "join_way": "영업점,인터넷,스마트폰",
  "cb_name": "KCB", "dcls_strt_day": "20261016", "dcls_end_day": "99991231", "fin_co_subm_day": "202610161557"
}
```

| 원문 | credit_loan_product 컬럼 | 비고 |
| --- | --- | --- |
| `fin_co_no` / `fin_prdt_cd` / `crdt_prdt_type` | `inst_code` / `product_code` / `credit_product_type` | **셋이 자연키.** 1 일반 / 2 마이너스한도 / 3 카드론 |
| `kor_co_nm` / `fin_prdt_nm` / `join_way` | `inst_name` / `product_name` / `join_way` | |
| `cb_name` | `cb_name` | 신용평가사 KCB / NICE. 다른 상품군에 없음 |
| 공시월·공시기간·제출일 | product 와 같은 규칙 | `"99991231"` → null |
| — | — | `join_deny`·`join_member`·`etc_note`·대출 문장 필드가 원문에 없다 |

## option → credit_loan_option

```json
{"fin_co_no": "0010001", "fin_prdt_cd": "CR0001A", "crdt_prdt_type": "1",
 "crdt_lend_rate_type": "A", "crdt_lend_rate_type_nm": "대출금리",
 "crdt_grad_1": 6.03, "crdt_grad_4": 6.83, "crdt_grad_5": 7.2, "crdt_grad_6": 7.45,
 "crdt_grad_10": 7.5, "crdt_grad_11": 7.4, "crdt_grad_12": 9.21, "crdt_grad_13": 9.12, "crdt_grad_avg": 6.62}
```

| 원문 | credit_loan_option 컬럼 | 비고 |
| --- | --- | --- |
| `crdt_lend_rate_type` | `lend_rate_type` | A 대출금리 / B 기준금리 / C 가산금리 / D 가감조정금리 → 상품당 최대 4행 |
| `crdt_grad_1` … `crdt_grad_13` (8개) | `rate_grade_1` … `rate_grade_13` | 신용점수 구간별 금리. 구간 번호 1,4,5,6,10,11,12,13 고정 |
| `crdt_grad_avg` | `rate_avg` | |

## 모양이 테이블을 정한 부분

- 같은 상품코드가 일반·마이너스한도·카드론으로 나뉘어 나온다(`0010016/WR0002F`, 80건 중 1건 중복)
  → `product` 의 자연키(기관+상품코드+상품군)로는 구분할 수 없어 `crdt_prdt_type` 을 키에 넣은 별도 테이블
- base 필드 구성이 다르다 — `cb_name` 이 있고 가입대상·기타·대출 문장 필드가 없다
- 옵션 한 건에 금리가 8~9개 들어 있다 → product_option 의 "옵션 한 건 = 금리 한 쌍" 구조와 맞지 않는다
- 구간이 고정이라 행으로 펼치지 않고 가로 컬럼으로 둔다. "어제 4구간 vs 오늘 4구간"이 같은 행 안의 비교가 된다
- 금리 종류(A~D)가 옵션을 가르므로 `(credit_loan_product_id, lend_rate_type)` 이 옵션 유일키다
