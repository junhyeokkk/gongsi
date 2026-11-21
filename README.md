# 공시 (gongsi)

금융감독원 금융상품통합비교공시 Open API 로 예금·적금·대출 상품과 금융회사를 매일 수집·정규화하고, 금리 변동을 감지해 이력으로 쌓는 서버. 조건 검색 API 와 금리 알림 구독은 다음 단계다.

**상태** (2026-11)

| 영역 | 상태 |
| --- | --- |
| 일일 수집 (fetch → normalize → diff) | **운영 중** — AWS 시드니 EC2 + RDS, KST 09:00~17:50 10분마다 판단 |
| 실패 재시도 · 슬랙 운영 알림 | **운영 중** — 10 → 30 → 60분 백오프, 완료 요약·실패·복구·중단 알림 |
| 상품 조회 API (`/api/products`) | 뼈대만 — 5주차 |
| 금리 알림 구독 (`/api/alerts`, notifyStep) | 뼈대만 — 6주차 |

## 한눈에

```
DailyCollectScheduler (10분마다, 오늘 실행 기록으로 판단)
  └ dailyCollectJob(targetDate)
      ① fetchStep      finlife API 6종 × 권역 4개 × 페이지 → raw_snapshot (JSONB 원문)
      ② normalizeStep  raw_snapshot → product·product_option / credit_loan_* / company·company_area
      ③ diffStep       오늘 vs 직전 수집일 원문 → rate_history·credit_rate_history·change_event
      ④ notifyStep     (구독 알림 자리, 아직 로그만)
```

- 수집 대상: 정기예금 · 적금 · 주택담보대출 · 전세자금대출 · 신용대출 · 금융회사
- 권역: 은행 `020000` · 저축은행 `030300` · 여신전문 `030200` · 보험 `050000`
- 같은 날 다시 띄우면 실패한 Step 부터 이어서 돈다. 모든 적재는 UPSERT 또는 중복 무시라 재실행해도 안전하다
- 상품이 하루에 max(5개, 10%) 넘게 생기거나 사라지면 원문 누락으로 보고 멈춘다 (`FAILED_NO_RETRY`, 자동 재시도 안 함)

전체 흐름·ERD·JSON 필드 → 컬럼 매핑·컬럼 사전은 [`docs/design/data-flow.pdf`](docs/design/data-flow.pdf) 에 슬라이드로 정리돼 있다.

## 기술 스택

Java 21 · Spring Boot 3.3 · Spring Batch 5.1 · PostgreSQL 16 · Flyway · JdbcTemplate (UPSERT) · springdoc-openapi · Testcontainers

## 로컬 실행

```bash
cp .env.example .env          # FINLIFE_API_KEY 채우기
docker compose up -d          # PostgreSQL (5434)
./gradlew bootRun             # Flyway 마이그레이션 자동 적용
```

하루치 수집을 수동으로 한 번 돌리기:

```bash
./gradlew bootRun --args='--spring.batch.job.enabled=true --spring.batch.job.name=dailyCollectJob targetDate=2026-11-20'
```

테스트 (Testcontainers, Docker 필요):

```bash
./gradlew test               # 단위 테스트
env CI=true ./gradlew test   # Postgres 재시작 통합 테스트 포함
```

### 환경 변수

| 이름 | 기본값 | 용도 |
| --- | --- | --- |
| `FINLIFE_API_KEY` | (없음) | 금감원 Open API 인증키 |
| `DB_HOST` · `DB_PORT` · `DB_NAME` · `DB_USER` · `DB_PASS` | `127.0.0.1` · `5434` · `gongsi` · `gongsi` · `gongsi` | PostgreSQL |
| `COLLECT_SCHEDULE_ENABLED` | `false` | 스케줄러 켜기 (운영만 `true`) |
| `COLLECT_CRON` | `0 */10 9-17 * * *` | 판단 주기 (KST) |
| `ALERT_SLACK_WEBHOOK_URL` | (없음) | 슬랙 운영 알림, 비우면 로그만 |

운영에서는 인증키·웹훅을 SSM Parameter Store 에 둔다.

### 포트

| 서비스 | 주소 |
| --- | --- |
| Spring Boot | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| PostgreSQL | localhost:5434 |

## 배포

main 에 머지하면 EC2 의 `gongsi-update.timer` 가 10분 안에 새 jar 를 받아 재기동한다. 수집 도중 재기동돼도 기동 시 끊긴 실행을 FAILED 로 정리하고 백오프 뒤 이어서 돈다. 구성·확인·수동 실행 절차는 [`docs/runbook/aws-deploy.md`](docs/runbook/aws-deploy.md).

## 코드 구조

```
src/main/java/com/gongsi/
  collect/
    job/        Job 구성 · 스케줄러 · 재시도 규칙 · 고아 실행 정리 · 슬랙 알림
    step/       fetch / normalize / diff / notify Step 정의
    fetch/      finlife API 호출 · raw_snapshot 적재
    normalize/  원문 파싱(parse) · 정규화 모델(model) · UPSERT(store)
    diff/       금리 변동 계산 · 이력 · change_event 적재
    notify/     구독 알림 (예정)
  serve/        조회 · 구독 REST API (예정)
  common/       상품군 코드 · 설정 · 공통 응답
src/main/resources/db/migration/   Flyway V1~V3
survey/                            사전 조사 스크립트와 원문 픽스처 (survey/raw)
deploy/                            EC2 배포 스크립트 · 데이터 이전 SQL
```

## 문서

| 위치 | 내용 |
| --- | --- |
| [`docs/design/`](docs/design/README.md) | 데이터 모델 · 상품군별 매핑 · 권역 · 금융회사 · API 명세 · 흐름 슬라이드 |
| [`docs/adr/`](docs/adr/README.md) | 아키텍처 결정 기록 (원문 보존, UPSERT, diff 기준, 호스팅, 재시도, 급변 차단, 고아 정리 등) |
| [`docs/runbook/`](docs/runbook/README.md) | 운영 절차 — 배포 · 데이터 이전 · 매일 확인 · 백업 |
| [`docs/conventions/`](docs/conventions/) | 커밋 · 브랜치 · PR · 코드 스타일 |
| [`docs/survey/`](docs/survey/) | 사전 조사 실측 기록 |

기획 문서(개요·도메인·기능·계획)는 Notion `EZ › 프로젝트 › 공시 (gongsi)`.
