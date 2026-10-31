# 금감원 Open API 개요

수집 대상인 금감원 "금융상품 한눈에" Open API(`https://finlife.fss.or.kr/finlifeapi`)가 무엇을 주고, 그중 무엇을 쓰는지.
✅ 는 직접 호출해 확인한 것, ❔ 는 개발가이드 기준이지만 아직 호출해 보지 않은 것이다.

## 서비스

| 서비스 | 내용 | 수집 | 확인 |
| --- | --- | --- | --- |
| `depositProductsSearch` | 정기예금 | O | ✅ |
| `savingProductsSearch` | 적금 | O | ✅ |
| `mortgageLoanProductsSearch` | 주택담보대출 | O | ✅ |
| `rentHouseLoanProductsSearch` | 전세자금대출 | O | ✅ |
| `creditLoanProductsSearch` | 개인신용대출 | O (정규화 미구현) | ✅ |
| `annuitySavingProductsSearch` | 연금저축 (보험·펀드·신탁) | X | ❔ |
| `companySearch` | 금융회사 정보 (회사명·홈페이지·콜센터·영업 지역) | O | ✅ |

- 상품 서비스는 모두 `baseList`(상품) + `optionList`(금리 등 조건) 구조다. 상품군별 모양은 [product-types/](product-types/README.md)
- `companySearch` 는 상품이 아니라 회사 목록이다. 회사 기본정보(`baseList`) + 시·도별 영업점 유무(`optionList`) ✅. 저장 방식은 [company.md](company.md)
- 연금저축은 금리 비교보다 수익률·공시이율 중심이라 우선순위가 낮다

## 요청

```
GET /finlifeapi/{서비스}.json?auth={인증키}&topFinGrpNo={권역}&pageNo={페이지}
```

| 파라미터 | 뜻 |
| --- | --- |
| `auth` | 인증키. `.env` / SSM 의 `FINLIFE_API_KEY` |
| `topFinGrpNo` | 권역 하나. 코드는 [fin-groups.md](fin-groups.md) |
| `pageNo` | 1부터. 응답의 `max_page_no` 까지 |

- **날짜 파라미터가 없다.** 항상 "현재 공시분"만 받는다 → 원문을 날짜별로 쌓는다(ADR-0001)
- 권역도 한 번에 하나만 → 서비스 × 권역 × 페이지마다 한 번 호출
- 호출 한도는 하루 10,000회. 지금 하루 17회(상품 15 + 회사 2)라 설계 고려 대상이 아니다 ✅
- 평소 응답 약 0.3초 ✅. 타임아웃·재시도 설정은 `application.yml` 의 `fss.*`

## 응답 공통 필드

```json
{"result": {"prdt_div": "D", "total_count": 40, "max_page_no": 1, "now_page_no": 1,
            "err_cd": "000", "err_msg": "정상", "baseList": [...], "optionList": [...]}}
```

| 필드 | 뜻 | 쓰는 곳 |
| --- | --- | --- |
| `err_cd` / `err_msg` | `000` 이 정상. 그 외는 HTTP 200 이어도 실패 | fetch 가 실패로 처리 |
| `total_count` | 그 권역의 전체 상품 수 (매 페이지 같은 값) | 로그·요약 |
| `max_page_no` / `now_page_no` | 전체 페이지 수 / 현재 페이지 | fetch 가 마지막 페이지까지 돎 |
| `prdt_div` | 상품 구분 (D 예금 / S 적금 / M 주담대 / R 전세 / C 신용) | 쓰지 않음 |

## 권역 × 서비스

| | 은행 | 저축은행 | 여신전문 | 보험 | 금융투자 |
| --- | --- | --- | --- | --- | --- |
| 예금·적금 | ✅ 있음 | ✅ 있음 | ❔ 거의 없음 | ❔ 없음 | ❔ 없음 |
| 주담대·전세 | ✅ 있음 | ✅ 있음 | ❔ 있을 수 있음 | ❔ 있을 수 있음 | ❔ 없음 |
| 신용대출 | ✅ 있음 | ✅ 있음 | ❔ 카드론 등 | ❔ 있을 수 있음 | ❔ 없음 |
| 연금저축 | ❔ 신탁 | — | — | ❔ 보험 | ❔ 펀드 |

❔ 칸은 미정 권역(여신전문·보험·금융투자)을 1회 호출해 확인한 뒤 이 표와 [fin-groups.md](fin-groups.md) 를 고친다.
상품이 없는 서비스·권역 조합이 빈 목록으로 오는지 오류 코드로 오는지도 그때 확인한다 — fetch 는 오류를 Step 실패로 보므로 넣기 전에 알아야 한다.
