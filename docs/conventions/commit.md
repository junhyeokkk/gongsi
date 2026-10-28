# 커밋 메시지 규약

## 형식

```
type(scope): subject

본문 (선택)
```

- `subject`: 한국어, 50자 이내, 마침표 없음, 명령형이 아닌 서술형 ("추가" / "수정")
- 본문은 **왜** 바꿨는지를 쓴다. 무엇을 바꿨는지는 diff가 말해준다.
- 한 커밋 = 한 가지 변경. subject에 "그리고"·"및"이 들어가면 쪼갠다.

## type

| type | 쓰는 때 |
| --- | --- |
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변경 없는 구조 변경 |
| `test` | 테스트만 추가·수정 |
| `docs` | README·ADR·docs/ 문서 |
| `chore` | 빌드·설정·스크립트·의존성 |

## scope

| scope | 영역 | 담당 |
| --- | --- | --- |
| `collect` | 수집 파이프라인 — Step ①②③, 클라이언트, 정규화, 변경 감지 | 준혁 |
| `serve` | 조회 API, 알림, 관리자 | 친구 |
| `common` | 공유 스키마, Flyway, CI, 설정 | 공동 |
| `survey` | 사전 조사 스크립트·기록 | 준혁 |

새 scope가 필요하면 이 표에 먼저 추가하고 쓴다.

## 예시

```
chore(survey): 사전 조사 수집 스크립트와 2026-10-28 응답 샘플
feat(collect): raw_snapshot 저장 Step — 같은 날짜 재실행 시 덮어쓰기
fix(serve): 알림 발송 실패분이 다음 실행에서 재발송되지 않던 문제
docs(adr): ADR-001 raw_snapshot 영구 보관
test(collect): 정규화 오염 레코드 격리 테스트 (AC-05)
```

## 하지 않는 것

- `update`, `fix bug`, `wip` 같은 내용 없는 subject
- 여러 scope를 한 커밋에 섞기 (`feat(collect,serve)` 금지 — 쪼갠다)
- 커밋 메시지에 Jira·이슈 번호 (이 프로젝트는 이슈 트래커 없음, AC 번호는 PR 본문에)
