# 브랜치 · PR 규약

## 브랜치

- `main`: 보호. 직접 push 금지, PR + 리뷰 1명 + CI 통과 시에만 머지
- 작업 브랜치: `type/scope-내용` — 예: `feat/collect-raw-fetch`, `fix/serve-alert-retry`, `docs/adr-001`
- 브랜치는 PR 머지 후 삭제

## PR

- 제목 = 대표 커밋 메시지 형식 (`type(scope): subject`)
- 본문: 관련 AC 번호, 확인 방법, 스크린샷·로그(해당 시)
- 리뷰어: 담당 영역의 상대방. `common` 변경은 양쪽 승인
- 머지 방식: squash (commit 규약은 squash 후 메시지에 적용)

## 리뷰

- (1주차 합의 후 작성)
