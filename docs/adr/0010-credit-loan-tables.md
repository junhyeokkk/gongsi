# 0010 신용대출 — credit_loan_* 별도 테이블, change_event 는 XOR 로 두 계열 공유

- 상태: 제안
- 날짜: 2026-10-28
- 담당: 준혁

## 문제

예금·적금·주담대·전세는 한 `product`·`product_option` 테이블에 nullable 컬럼으로 모았다. 신용대출도 같이 넣을지 정해야 했다.

- 신용대출만 `fin_co_no + fin_prdt_cd` 가 유일하지 않다. `0010016/WR0002F` 가 마이너스한도·카드론 두 행이다
- 옵션이 "기간·유형별 금리 1개"가 아니라 "금리 종류(A~D) × 신용등급 구간 8개"다

## 선택지

1. 신용대출도 `product`·`product_option` 에 넣는다
2. **`credit_loan_product`·`credit_loan_option`·`credit_rate_history` 로 분리하고, `change_event` 하나를 두 계열이 함께 쓴다**

## 결정

2번.

- 상품 자연키 `(fin_co_no, fin_prdt_cd, crdt_prdt_type)`, 옵션키는 금리 종류 A~D
- 등급 구간은 가로 컬럼 `rate_grade_1` … `rate_grade_13`(8개)·`rate_avg`
- `change_event` 는 `product_id` 와 `credit_loan_product_id` 중 정확히 하나만 채운다 — `chk_change_event_product_xor`(V1)

근거와 테이블 구성은 [data-model.md 2절](../design/data-model.md#신용대출을-따로-둔-이유).

## 근거

- 자연키가 다르다. `product` 의 유니크 제약을 상품군마다 다르게 걸 수 없다
- 등급 구간은 고정이라 가로 컬럼이면 같은 구간끼리 1:1 비교가 단순하다
- 두 계열의 변동을 이벤트 테이블 하나에 모은다. XOR 제약으로 한 행이 두 계열을 동시에 가리키거나 아무것도 가리키지 않는 것을 DB 가 막는다

## 결과 · 트레이드오프

- 얻은 것: 신용대출 자연키·옵션 구조를 그대로 담는다. 알림은 change_event 하나만 본다
- 포기한 것: 조회·정규화·diff 가 두 계열이 된다. 원문 순회는 `RawRecordReader`·`RecordParser` 로, diff 는 자연키 제네릭과 `ProductFamily` 로 공통화했다(PR #9)
- 금리 종류 A~D 를 모두 비교해 기준금리(B)가 바뀌면 대출금리(A)와 함께 이벤트가 2건 생긴다. 알림에서 무엇을 쓸지는 notify 단계에서 정한다
- 실측: 매일 상품 115 / 옵션 284 / 격리 0(2026-11-08~11-13)
