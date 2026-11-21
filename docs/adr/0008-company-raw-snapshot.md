# 0008 금융회사 원문 — raw_snapshot 에 product_type=COMPANY 로 함께 보관

- 상태: 제안
- 날짜: 2026-10-31
- 담당: 준혁

## 문제

금융회사 목록(`companySearch`)도 날짜 파라미터가 없어 그날 받지 못하면 다시 받을 수 없다. 상품 원문처럼 날짜별로 남겨야 하는데, 어디에 둘지 정해야 했다.

## 선택지

1. 회사 원문 전용 테이블을 따로 만든다
2. **상품 원문과 같은 `raw_snapshot` 에 `product_type = 'COMPANY'` 로 둔다**

## 결정

2번. 상품과 같은 권역으로 받아 `raw_snapshot(product_type='COMPANY')` 에 두고, normalizeStep 에서 상품 다음에 `company`·`company_area` 로 정규화한다.
상세는 [company.md](../design/company.md).

## 근거

- 키 `(fetched_on, product_type, fin_grp_no, page_no)` 와 페이지 단위 구조가 상품 원문과 같다
- 날짜별 원문 영구 보관·S3 일별 백업(ADR-0001)을 별도 작업 없이 그대로 탄다
- 정규화 오류 격리도 같은 `normalize_error` 를 쓴다(ADR-0007)

## 결과 · 트레이드오프

- 얻은 것: 수집·보관·백업 경로가 하나
- 포기한 것: `product_type` 컬럼에 상품군이 아닌 값이 섞인다. 상품만 읽는 쿼리는 `COMPANY` 를 빼야 한다
- 하루 API 호출 15 → 17회(PR #6 시점, 2권역)
- `product.inst_code → company.fin_co_no` FK 는 걸지 않는다. 회사 목록에 없는 상품 회사가 실제로 있다(아이엠뱅크 `0010016`)
