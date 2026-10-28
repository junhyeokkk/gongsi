# 공시 (gongsi)

금융감독원 금융상품통합비교공시 Open API로 예금·적금·대출 상품을 매일 수집·정규화하고, 금리 변동을 감지해 조건 구독자에게 알리며, 조건 검색·비교 API를 제공하는 서버.

**상태**: 사전 조사 중 (2026.11 ~ 12) · 개발 착수 2027.01

## 사전 조사

```bash
cp .env.example .env   # FINLIFE_API_KEY 채우기
python survey/fetch.py # 서비스 × 권역 × 전체 페이지 → survey/raw/
```

- `survey/raw/{service}_{group}_p{page}_{YYYYMMDD}.json` — 응답 원문. 정규화 검증·테스트 픽스처 원본
- `survey/errors.log` — 호출 실패 기록 (커밋 제외)
- 실행 끝에 서비스·권역별 total_count / 페이지 수 / base·option 행 수 요약 출력

## 문서

- 설계(개요·도메인·기능·데이터 모델·계획): Notion `EZ › 프로젝트 › 공시 (gongsi)`
- 협업 규약·ADR·API 계약·사전 조사 기록: [`docs/`](docs/README.md)
