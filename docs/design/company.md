# 금융회사 (companySearch)

금감원 `companySearch` 로 받는 금융회사 목록을 어디에 어떻게 남기는지.
API 전반은 [fss-api.md](fss-api.md), 권역은 [fin-groups.md](fin-groups.md), 전체 테이블은 [data-model.md](data-model.md).

## 왜 받나

- 상품 원문에는 회사명(`kor_co_nm`)만 있고 홈페이지·콜센터가 없다
- 권역도 상품 원문에는 없어 요청 권역으로만 알 수 있다. 회사 목록을 따로 받으면 권역을 **회사 단위로** 한 번에 붙일 수 있다
- 시·도별 영업점 유무가 함께 와서 "부산에 지점 있는 저축은행" 같은 조회가 가능해진다

## 원문 (2026-10-31 실측)

요청·응답 공통 구조는 상품 서비스와 같다(`baseList` + `optionList`, `err_cd`, `max_page_no`). `prdt_div` 는 `F`.

| 권역 | 페이지 | 회사(base) | 지역(option) |
| --- | --- | --- | --- |
| 은행 020000 | 1 | 17 | 289 |
| 저축은행 030300 | 1 | 80 | 1,360 |

```json
{"baseList": [{"dcls_month": "202610", "fin_co_no": "0010001", "kor_co_nm": "우리은행",
               "dcls_chrg_man": "개인상품마케팅부, 1588-5000 ...", "homp_url": "https://...", "cal_tel": "15885000"}],
 "optionList": [{"dcls_month": "202610", "fin_co_no": "0010001", "area_cd": "01", "area_nm": "서울", "exis_yn": "Y"}]}
```

- 회사마다 17개 시·도(`01` 서울 … `17` 제주)가 **모두** 온다. 영업점이 없으면 `exis_yn=N`
- 두 권역에 함께 나오는 회사는 0건. 권역 필드는 여기에도 없다
- 원문 샘플: `survey/raw/companySearch_{권역}_p1_20261031.json`

## 수집

- 상품과 같은 `FetchService` 가 같은 권역(`FIN_GRP_NOS`)으로 돈다 — 상품군(`ProductType`)이 아니라 별도 데이터셋이다
- 원문은 `raw_snapshot` 에 `product_type = 'COMPANY'` 로 둔다. 날짜별 원문 보관·S3 백업(ADR-0001)을 그대로 탄다
- 실패하면 상품 실패와 똑같이 fetchStep 이 실패한다 — 회사가 빠진 채 정규화하면 전부 소멸로 오판한다
- 하루 호출이 15회 → 17회로 는다

## 테이블 (V3)

```
company ─< company_area
```

| company | 원문 | 비고 |
| --- | --- | --- |
| `fin_co_no` | `fin_co_no` | 자연키. `product.inst_code` 와 같은 값 |
| `fin_grp_no` | (요청 권역) | `raw_snapshot.fin_grp_no` 를 옮겨 적음 |
| `company_name` | `kor_co_nm` | |
| `homepage_url` | `homp_url` | 최대 300자 |
| `call_tel` | `cal_tel` | 숫자만, 최대 20자 |
| `disclosure_contact` | `dcls_chrg_man` | 부서·전화 자유 서술 |
| `disclosure_month` | `dcls_month` | |
| `is_active` / `first_seen_on` / `last_seen_on` | — | product 와 같은 생명주기 |

| company_area | 원문 | 비고 |
| --- | --- | --- |
| `(company_id, area_code)` | `fin_co_no`, `area_cd` | 자연키 |
| `area_name` | `area_nm` | |
| `has_branch` | `exis_yn` | Y → true |
| `last_seen_on` | — | 17개 시·도가 매번 다 오므로 `is_active` 는 두지 않음 |

- 시·도 코드표 테이블은 따로 두지 않는다. 17개 고정이고 원문이 이름을 함께 준다
- `product.inst_code → company.fin_co_no` FK 는 걸지 않는다. 회사 목록에 없는 상품 회사가 실제로 있다(아래)

## 정규화

`collect/normalize` 상품 정규화와 같은 구조다(parse/model/store, JdbcTemplate UPSERT — ADR-0002).

| 위치 | 클래스 |
| --- | --- |
| parse | `CompanyNormalizer` |
| model | `NormalizedCompany`, `NormalizedCompanyArea` |
| store | `CompanyUpsertService` |
| 흐름 | `CompanyNormalizeService` — normalizeStep 에서 상품 다음에 한 트랜잭션으로 |

- 격리 사유는 상품과 같다: 회사명·`fin_co_no` 없음 `MISSING_REQUIRED`, 길이 초과 `TYPE_MISMATCH`, `exis_yn` 이 Y/N 밖 `UNKNOWN_ENUM`, 정상 회사가 없는 지역 `ORPHAN_OPTION`. `normalize_error.product_type = 'COMPANY'`
- 시·도 코드는 새 값이 생겨도 격리하지 않도록 길이만 본다
- 그날 원문에 없던 회사는 `is_active = false`
- 그날 `COMPANY` 원문이 아예 없으면 경고만 남기고 건너뛴다 — 도입 전 날짜를 재처리할 때 회사가 전부 비활성이 되지 않게

## 실측 (로컬 DB, 2026-10-31)

| 권역 | 회사 | 지역 행 | 영업점 있음 | 격리 |
| --- | --- | --- | --- | --- |
| 은행 | 17 | 289 | 218 | 0 |
| 저축은행 | 80 | 1,360 | 135 | 0 |

- 상품 원문의 회사 97곳 중 96곳이 회사 목록에 있다. **아이엠뱅크(`0010016`)는 상품은 있는데 은행 회사 목록에 없다** —
  원인은 확인하지 않았다(시중은행 전환 뒤 회사 목록 반영이 늦은 것으로 추정). 그래서 상품 권역은 계속 `product.fin_grp_no`(V2)로 따로 남긴다
