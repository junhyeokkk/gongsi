package com.gongsi.collect.fetch;

import com.gongsi.common.ProductType;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

/** raw_snapshot 테이블 쓰기 전담. 멱등 UPSERT (uk_raw_snapshot 충돌 시 payload 업데이트) */
@Repository
public class RawSnapshotRepository {

    // TODO 2주차: JdbcTemplate 주입
    // TODO 2주차: insertOrUpdate(fetchedOn, type, finGrpNo, pageNo, payload) 구현
    //   SQL: INSERT ... ON CONFLICT (fetched_on, product_type, fin_grp_no, page_no)
    //        DO UPDATE SET payload = EXCLUDED.payload, fetched_at = now()
    //        RETURNING id
    // TODO 3주차: findByDate(fetchedOn, type) — Normalize Step 이 당일 raw 전부 로드

    public long insertOrUpdate(LocalDate fetchedOn, ProductType type, String finGrpNo, int pageNo, String payload) {
        throw new UnsupportedOperationException("2주차 구현 예정");
    }
}
