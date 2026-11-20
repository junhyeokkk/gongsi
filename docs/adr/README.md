# ADR (Architecture Decision Records)

구현 방식을 선택한 이유를 기록한다. 코드가 바뀌면 함께 바뀌어야 하므로 레포에 둔다.

## 규칙

- 파일명: `NNNN-제목.md` (4자리 순번, 소문자-하이픈). 예: `0001-raw-snapshot-retention.md`
- 상태: `제안` → `확정` → `대체됨(NNNN)`. **확정된 ADR은 수정하지 않는다.** 바꾸려면 새 ADR을 쓰고 이전 것을 `대체됨`으로 표시한다.
- 한 ADR = 한 결정. 두 결정이 얽혀 있으면 둘로 나누고 서로 링크한다.
- `template.md`를 복사해서 시작한다.

## 목록

| 번호 | 제목 | 담당 | 상태 |
| --- | --- | --- | --- |
| 0001 | raw_snapshot 보관 정책 | 준혁 | 확정 |
| 0002 | product/option 적재 방식 (JPA vs JdbcTemplate) | 준혁 | 확정 |
| [0003](0003-diff-baseline.md) | 변경 감지 비교 기준 · 결측일 처리 — 직전 수집일 원문 재정규화 | 준혁 | 제안 |
| 0004 | 알림 발송 실패 재시도 정책 | 친구 | 제안 |
| 0005 | 검색 성능 — 인덱스 vs 캐시 | 친구 | 제안 |
| [0006](0006-ec2-rds-hosting.md) | 운영 호스팅 — EC2 + RDS, 크레딧 관찰 | 준혁 | 제안 |
| [0007](0007-normalize-error-isolation.md) | 정규화 오류 격리 — 레코드 단위로 normalize_error 에 남기고 계속 | 준혁 | 제안 |
| [0008](0008-company-raw-snapshot.md) | 금융회사 원문 — raw_snapshot 에 product_type=COMPANY 로 함께 보관 | 준혁 | 제안 |
| [0009](0009-fin-groups.md) | 수집 권역 — 은행·저축은행·여신전문·보험, 금융투자 제외 | 준혁 | 제안 |
| [0010](0010-credit-loan-tables.md) | 신용대출 — credit_loan_* 별도 테이블, change_event XOR | 준혁 | 제안 |
| [0011](0011-collect-retry-backoff.md) | 수집 실패 재시도 — 배치 메타 기반 판단, 10/30/60분 백오프 | 준혁 | 제안 |
| [0012](0012-mass-change-no-retry.md) | 급변 차단 — 재시도하지 않고 FAILED_NO_RETRY 로 멈춤 | 준혁 | 제안 |
| [0013](0013-slack-ops-alert.md) | 운영 알림 — 슬랙 Incoming Webhook, 발송 실패는 무시 | 준혁 | 제안 |
| [0014](0014-orphan-execution-cleanup.md) | 고아 실행 정리 — 기동 시 이전 JVM 의 running 실행을 FAILED 로 닫음 | 준혁 | 제안 |
