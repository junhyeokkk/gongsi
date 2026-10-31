# 데이터 모델과 정규화 설계

금감원 금융상품 원문을 어떻게 테이블로 나눴고 상품군별로 어떻게 정규화하는지, 그리고 왜 그렇게 했는지.
스키마 정본은 `src/main/resources/db/migration/V1__init.sql`, 정규화 코드는 `collect/normalize/` 다.
상품군별 원문 예시와 필드 매핑은 [product-types/](product-types/README.md).

## 1. 원문에서 확인한 것

근거: [2026-10-28 첫 전체 수집](../survey/2026-10-28-first-fetch.md) (서비스 5 × 권역 2, 상품 ~1,000 / 옵션 ~5,000)

| 관찰 | 설계에 준 영향 |
| --- | --- |
| 응답은 `baseList`(상품) + `optionList`(금리 조건) 두 목록이고 `fin_co_no + fin_prdt_cd` 로 이어진다 | 상품·옵션 2단 테이블, 옵션은 상품 FK |
| `fin_co_no + fin_prdt_cd` 가 예금·적금·주담대·전세에서 유일 | 이 둘 + `product_type` 을 product 자연키로 |
| 신용대출만 1건 중복 — 같은 상품코드가 마이너스한도·카드론 두 행 | 신용대출 자연키에 `crdt_prdt_type` 추가 → 별도 테이블 |
| 신용대출 base 는 `cb_name` 이 있고 `join_member`·`etc_note` 가 없음, 옵션은 신용등급 구간별 금리 | 필드 구성이 달라 별도 테이블 |
| API 에 날짜 파라미터가 없음 | 원문을 날짜별로 그대로 남김(raw_snapshot, ADR-0001) |
| `dcls_end_day` 는 null·실제 날짜·`"99991231"` 이 섞임 | `"99991231"` 은 "종료일 없음" → null 로 통일 |
| `intr_rate` null 0건, `lend_rate_avg` null 170/268(주담대) | 평균금리는 nullable, 변동 감지에서 제외 |
| `max_limit` null 398/435(예금) | 저장은 하되 검색 조건으로 쓰지 않음 |
| 금리는 JSON 숫자, `save_trm` 만 문자열 | 기간은 정규화 때 정수로 변환, 실패하면 오류로 격리 |
| 은행·저축은행 구분이 레코드 안에 없고 요청 파라미터(권역)로만 갈린다. 두 권역에 함께 나오는 상품·회사는 0건(2026-10-30) | 정규화 때 페이지의 권역을 `product.fin_grp_no` 로 옮겨 적는다(V2). 자연키에는 넣지 않는다 |

## 2. 테이블을 나눈 방식

```
raw_snapshot          날짜·상품군·권역·페이지 단위 원문 (수집 전용)
normalize_error       정규화하지 못한 레코드와 사유 (수집 전용)

product ─< product_option ─< rate_history                          예금·적금·주담대·전세
credit_loan_product ─< credit_loan_option ─< credit_rate_history   신용대출
change_event          두 계열의 변동 이벤트 (product_id XOR credit_loan_product_id)
```

### 원문과 정규화 결과를 분리한 이유

- 원문은 다시 받을 수 없다. 정규화 규칙이 바뀌거나 버그가 나면 원문부터 다시 돌려야 한다
- 정규화 결과는 "오늘 기준 상품 목록"이고, 원문은 "그날 받은 그대로"다. 성격이 달라 한 테이블에 둘 수 없다

### 예금·적금·주담대·전세를 한 테이블(product)에 둔 이유

- base 필드의 대부분(기관·상품명·가입방법·공시월·공시기간)이 같다
- 상품군 전용 필드는 7개뿐이다 — 예·적금 3개(`max_limit`, `special_condition`, `maturity_interest_note`),
  대출 4개(`loan_limit_note`, `early_repayment_fee_note`, `delinquency_rate_note`, `incidental_expense_note`)
- 상품군별 테이블 4개로 나누면 조회 API·변동 감지·알림이 모두 4갈래가 된다. nullable 컬럼 7개가 더 싸다

옵션도 같은 이유로 product_option 한 테이블에 상품군별 컬럼을 nullable 로 둔다.

| 상품군 | 쓰는 옵션 컬럼 |
| --- | --- |
| 예금 DEP | `save_term`, `rate_type`, `base_rate`, `max_rate` |
| 적금 SAV | 예금 + `reserve_type` |
| 주담대 MORT | `mortgage_type`, `repay_type`, `lend_rate_type`, `rate_min`, `rate_max`, `rate_avg` |
| 전세 RENT | 주담대에서 `mortgage_type` 제외 |

### 신용대출을 따로 둔 이유

- 자연키가 다르다(`crdt_prdt_type` 포함). product 의 유니크 제약을 상품군마다 다르게 걸 수 없다
- 옵션이 "기간·유형별 금리 1개"가 아니라 "금리 종류(A 대출/B 기준/C 가산/D 가감) × 신용등급 구간 8개"다
- 구간 8개는 행으로 펼치지 않고 가로 컬럼(`rate_grade_1` … `rate_grade_13`, `rate_avg`)으로 둔다.
  구간은 고정이고, 같은 구간끼리 1:1 비교가 단순해진다

## 3. 행의 생명주기

| 컬럼 | 의미 | 갱신 규칙 |
| --- | --- | --- |
| `first_seen_on` | 처음 본 날 | 지금까지 본 날 중 가장 이른 날 (`LEAST`) |
| `last_seen_on` | 마지막으로 본 날 | 가장 늦은 날 (`GREATEST`) |
| `is_active` | 오늘 원문에 있는가 | 그날 원문에 없으면 false |

- 상품이 원문에서 사라져도 행을 지우지 않는다. 금리 이력·변동 이벤트가 이 행을 FK 로 가리킨다
- 날짜를 역순으로 재처리해도 first/last 가 틀어지지 않게 LEAST/GREATEST 를 쓴다

## 4. 정규화 설계

### 흐름

상품군 하나 = 트랜잭션 하나. 그날 그 상품군의 원문 전 페이지를 읽어서:

1. baseList → 상품 행, optionList → 옵션 행으로 변환. 실패한 레코드만 `normalize_error` 로 격리하고 계속
2. 모든 페이지를 다 본 뒤 정상 base 가 없는 옵션을 `ORPHAN_OPTION` 으로 격리 — base 와 옵션이 다른 페이지에 있을 수 있다
3. 상품 UPSERT → product id 조회 → 옵션 UPSERT (JdbcTemplate, ADR-0002)
4. 그날 안 보인 상품·옵션을 비활성으로

원문이 아예 없는 상품군이 있으면 Step 을 실패시킨다. 그대로 진행하면 전 상품을 소멸로 오판한다.

### 옵션키

옵션은 원문에 식별자가 없다. 상품 안에서 옵션을 구분하는 필드를 이어 붙여 `option_key` 를 만든다.

| 상품군 | 옵션키 | 예 | 근거 |
| --- | --- | --- | --- |
| 예금 | 기간\|금리유형 | `12\|S` | (save_trm + intr_rate_type) 2,816/2,816 유일 |
| 적금 | 기간\|금리유형\|적립유형 | `12\|S\|F` | 같은 기간·금리유형에 정액(S)·자유(F) 적립이 함께 있다 |
| 주담대 | 담보\|상환\|금리유형 | `A\|D\|F` | (mrtg + rpay + lend_rate_type) 268/268 유일 |
| 전세 | 상환\|금리유형 | `D\|C` | 담보유형 필드가 없다 |

옵션키가 날마다 같아야 금리를 어제 값과 1:1 로 비교할 수 있다(변동 감지, ADR-0003).

### 격리 사유

한 레코드가 배치 전체를 깨뜨리지 않게, 오류는 레코드 단위로 걸러 사유를 남긴다.

| 사유 | 조건 | 왜 격리하나 |
| --- | --- | --- |
| `MISSING_REQUIRED` | 기관코드·상품코드·기관명·상품명·공시월, 옵션의 기간·유형이 없음 | 자연키·옵션키를 만들 수 없다 |
| `TYPE_MISMATCH` | 기간이 숫자가 아님, 날짜가 `yyyyMMdd`·`yyyyMMddHHmm` 이 아님, 금리 > 999.99, 문자열이 컬럼 길이 초과 | 그대로 넣으면 DB 오류로 상품군 전체가 롤백된다 |
| `UNKNOWN_ENUM` | 코드값이 정의 밖 (금리유형 S/M, 적립 S/F, 담보 A/E, 상환 D/S, 대출금리 F/C, 가입제한 1/2/3) | 모르는 코드로 옵션키를 만들면 다음 날 비교가 어긋난다 |
| `ORPHAN_OPTION` | 같은 날 원문에 정상 base 가 없는 옵션 | 부모 product 가 없어 FK 를 걸 수 없다 |

`detail` 에는 원문 전체가 아니라 `기관코드/상품코드: 사유` 만 남긴다. 원문은 `raw_snapshot_id` 로 찾아간다.

### 값 변환

- `dcls_end_day = "99991231"` → null (종료일 없음)
- `save_trm` 문자열 → SMALLINT
- 빈 문자열 → null
- 상품군에 없는 필드는 null (예금의 `loan_lmt` 등)

## 5. 실측 결과

로컬 DB, 2026-10-30 원문

| 상품군 | 페이지 | 상품 | 옵션 | 격리 |
| --- | --- | --- | --- | --- |
| DEP | 5 | 436 | 2,818 | 0 |
| SAV | 4 | 337 | 1,534 | 0 |
| MORT | 2 | 92 | 268 | 0 |
| RENT | 2 | 49 | 96 | 0 |

## 6. 아직 안 한 것

- 신용대출 정규화 (`credit_loan_*` 적재)
- 여신전문(030200)·보험(050000)·금융투자(060000) 권역 포함 여부
