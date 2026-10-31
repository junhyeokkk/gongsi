package com.gongsi.collect.fetch;

import com.gongsi.common.ProductType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

// raw_snapshot 테이블 쓰기 전담 멱등 UPSERT (uk_raw_snapshot 충돌 시 payload 업데이트)
@Repository
public class RawSnapshotRepository {

    // 금융회사 원문의 product_type 값
    public static final String COMPANY = "COMPANY";

    // 재실행해도 페이지당 한 행 (EXCLUDED = 새로 넣으려던 행)
    private static final String UPSERT_SQL = """
            INSERT INTO raw_snapshot (fetched_on, product_type, fin_grp_no, page_no, payload, fetched_at)
            VALUES (?, ?, ?, ?, ?::jsonb, now())
            ON CONFLICT (fetched_on, product_type, fin_grp_no, page_no)
            DO UPDATE SET payload = EXCLUDED.payload, fetched_at = now()
            RETURNING id
            """;

    // ON CONFLICT 는 JPA 로 못 써서 SQL 직접 사용
    private final JdbcTemplate jdbc;

    public RawSnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 한 페이지 원문을 적재하고 행 id 반환 - 같은 날,같은 페이지면 덮어씀
    public long insertOrUpdate(LocalDate fetchedOn, ProductType type, String finGrpNo, int pageNo, String payload) {
        return insertOrUpdate(fetchedOn, type.name(), finGrpNo, pageNo, payload);
    }

    // dataset: product_type 컬럼 값 - 상품군 코드 또는 COMPANY
    public long insertOrUpdate(LocalDate fetchedOn, String dataset, String finGrpNo, int pageNo, String payload) {
        return jdbc.queryForObject(UPSERT_SQL, Long.class, fetchedOn, dataset, finGrpNo, pageNo, payload);
    }

    // 그날 한 상품군의 원문 전 페이지를 권역·페이지 순으로 로드
    public List<RawPage> findByDate(LocalDate fetchedOn, ProductType type) {
        return findByDate(fetchedOn, type.name());
    }

    public List<RawPage> findByDate(LocalDate fetchedOn, String dataset) {
        return jdbc.query("""
                        SELECT id, fin_grp_no, payload::text AS payload FROM raw_snapshot
                        WHERE fetched_on = ? AND product_type = ?
                        ORDER BY fin_grp_no, page_no
                        """,
                (rs, i) -> new RawPage(rs.getLong("id"), rs.getString("fin_grp_no"), rs.getString("payload")),
                fetchedOn, dataset);
    }

    // raw_snapshot 한 행 - normalize_error 가 id 로 원문을 가리킨다
    public record RawPage(long id, String finGrpNo, String payload) {
    }
}
