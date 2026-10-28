# 공시 (gongsi)

금융감독원 금융상품통합비교공시 Open API로 예금·적금·대출 상품을 매일 수집·정규화하고, 금리 변동을 감지해 조건 구독자에게 알리며, 조건 검색·비교 API를 제공하는 서버.

**상태**: 사전 조사 중 (2026.11 ~ 12) · 1주차 뼈대 작업 완료 · 개발 착수 2027.01

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
## 개발 시작

```bash
# 1. 환경 변수 설정
cp .env.example .env   # FINLIFE_API_KEY, DB_PASS 채우기

# 2. PostgreSQL 기동
docker compose up -d

# 3. 서버 실행 (Flyway 마이그레이션 자동 적용)
./gradlew bootRun

# 4. Job 수동 실행 (날짜 파라미터 필수)
./gradlew bootRun --args='--spring.batch.job.name=dailyCollectJob targetDate=2026-10-28'

# 5. 테스트 (Testcontainers — Docker 필요)
./gradlew test
```

### 포트

| 서비스 | 포트 |
|---|---|
| Spring Boot | 8080 |
| PostgreSQL | 5432 |
| Swagger UI | http://localhost:8080/swagger-ui.html |

