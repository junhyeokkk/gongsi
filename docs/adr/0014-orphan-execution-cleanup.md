# 0014 고아 실행 정리 — 기동 시 이전 JVM 의 running 실행을 FAILED 로 닫음

- 상태: 제안
- 날짜: 2026-11-17
- 담당: 준혁

## 문제

수집 도중 앱이 재기동(배포·장애)되면 JobExecution 이 `STARTED` 로 남는다. `RetryPolicy.decide`(ADR-0011)는 이를 실행 중으로 보고 계속 건너뛰어, 그날 자동 재시도와 알림이 멈췄다.
같은 targetDate 를 다시 띄우면 `JobExecutionAlreadyRunningException` 이 난다.

## 선택지

1. 메타 테이블에서 손으로 `ABANDONED`·`FAILED` 처리한 뒤 수동 실행한다(PR #12 시점 런북)
2. **앱이 뜰 때 이전 JVM 이 남긴 running 실행을 찾아 FAILED 로 닫는다**

## 결정

2번. `OrphanExecutionCleaner`(ApplicationRunner, 최우선 순서)가 기동 시 정리한다.

- `findRunningJobExecutions` 중 이 JVM 기동 시각 전에 만든 실행만 고른다(`OrphanExecutions`)
- Job 과 미완료 Step 을 FAILED(종료시각·ExitStatus 포함)로 `JobRepository.update`
- 슬랙 "중단된 수집 정리" 를 보낸다(ADR-0013)
- 수동 실행(`spring.batch.job.enabled=true`) JVM 에서도 Job 보다 먼저 돈다

## 근거

- 정리 뒤에는 일반 실패로 읽혀, 다음 틱부터 기존 백오프대로 끊긴 Step 부터 재시도한다. 재시도 규칙을 바꾸지 않는다
- 배포는 10분마다 pull 이라(ADR-0006) 수집 중 재기동이 실제로 일어난다. 그때마다 DB 를 손으로 고칠 수 없다

## 결과 · 트레이드오프

- 얻은 것: 재기동 뒤 사람 손 없이 재개. 런북에서 수동 DB 수정 절차가 빠졌다
- 전제: 수집 앱은 EC2 1대다(ADR-0006). 기동 전에 만든 running 실행은 모두 죽은 것으로 본다. 앱을 두 대 이상 띄우면 다른 인스턴스의 정상 실행까지 FAILED 로 닫으므로, 그때는 이 결정을 다시 해야 한다
