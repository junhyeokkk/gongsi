# 0002 product/option 적재 방식 — JdbcTemplate UPSERT

- 상태: 확정
- 날짜: 2026-10-31
- 담당: 준혁

## 문제

normalize Step 은 매일 raw_snapshot 원문(상품 ~1,000 / 옵션 ~5,000)을 product · product_option 에 반영한다.

제약
- 같은 날짜를 다시 돌려도 결과가 같아야 한다(멱등). 자연키는 `uk_product(inst_code, product_code, product_type)`,
  `uk_product_option(product_id, option_key)`
- `first_seen_on` 은 처음 본 날로 고정, `last_seen_on`·내용은 갱신 — 행마다 "있으면 일부 컬럼만 갱신"이 필요하다
- 오염 레코드 하나가 배치 전체를 깨뜨리면 안 된다 — 레코드 단위로 격리하고 나머지는 계속
- 혼자 운영 — 적재 경로가 눈에 보이고 디버깅이 쉬워야 한다

## 선택지

1. **JPA 엔티티 + `saveAll`** — 조회 후 있으면 수정, 없으면 생성
2. **JdbcTemplate + `INSERT … ON CONFLICT DO UPDATE` 배치**
3. JPA 엔티티는 두고 UPSERT 만 `@Query(nativeQuery)` 로

## 결정

2번. JdbcTemplate `batchUpdate` 로 `ON CONFLICT … DO UPDATE` 를 보내고, product id 는 상품군 단위로 한 번 다시
조회해 옵션 적재에 쓴다. 엔티티 클래스는 만들지 않는다.

## 근거

- UPSERT 를 DB 가 원자적으로 처리한다. JPA 의 조회→판정→저장은 행마다 SELECT 가 붙고(~6,000회),
  `first_seen_on` 을 지키는 "일부 컬럼만 갱신" 규칙을 자바 코드로 다시 써야 한다
- raw_snapshot 적재(`RawSnapshotRepository`)가 이미 같은 방식이다 — 수집 쪽 쓰기 경로가 하나로 통일된다
- 3번은 엔티티와 네이티브 쿼리가 같은 테이블을 이중으로 정의해 둘이 어긋날 위험만 늘어난다
- 수집 쪽은 쓰기 위주라 연관관계·지연로딩·더티체킹 같은 JPA 이점을 거의 쓰지 않는다

## 결과 · 트레이드오프

- 얻은 것: 상품군 하나 = 배치 2번 + 조회 1번. 재실행 멱등. SQL 이 코드에 그대로 보여 튜닝·디버깅이 쉽다
- 포기한 것: 컬럼 추가 시 SQL 문자열과 record 를 손으로 같이 고쳐야 한다(컴파일러가 못 잡음 — 테스트로 보완)
- 제공(serve) 쪽 조회는 이 결정과 무관하다. 읽기 모델은 JPA 를 써도 된다
- 날짜를 역순으로 재처리하면 내용 컬럼은 마지막으로 처리한 날 값이 남는다. `first_seen_on`/`last_seen_on` 은
  LEAST/GREATEST 로 순서와 무관하게 맞춘다
