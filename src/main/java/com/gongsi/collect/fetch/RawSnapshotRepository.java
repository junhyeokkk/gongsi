package com.gongsi.collect.fetch;

import com.gongsi.common.ProductType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

/** raw_snapshot 테이블 쓰기 전담. 멱등 UPSERT (uk_raw_snapshot 충돌 시 payload 업데이트) */
@Repository
public class RawSnapshotRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO raw_snapshot (fetched_on, product_type, fin_grp_no, page_no, payload, fetched_at)
            VALUES (?, ?, ?, ?, ?::jsonb, now())
            ON CONFLICT (fetched_on, product_type, fin_grp_no, page_no)
            DO UPDATE SET payload = EXCLUDED.payload, fetched_at = now()
            RETURNING id
            """;

    private final JdbcTemplate jdbc;

    public RawSnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // TODO 3주차: findByDate(fetchedOn, type) — Normalize Step 이 당일 raw 전부 로드

    /** 한 페이지 원문을 적재하고 행 id 반환. 같은 날·같은 페이지면 덮어씀 */
    public long insertOrUpdate(LocalDate fetchedOn, ProductType type, String finGrpNo, int pageNo, String payload) {
        return jdbc.queryForObject(UPSERT_SQL, Long.class, fetchedOn, type.name(), finGrpNo, pageNo, payload);
    }
}
