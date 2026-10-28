# API 계약

제공 측 REST API의 계약을 적는다. Swagger(springdoc)가 정본이고, 이 폴더는 Swagger에 담기 어려운 설계 의도·규약·예시를 보완한다.

## 규칙

- 공통 규약(응답 형식·에러 형식·페이징·인증)은 `common.md` 하나에
- 리소스별 문서는 `products.md`, `alerts.md`, `admin.md` — 3주차 이후 작성
- 요청·응답 예시는 실제 호출 결과를 붙인다
