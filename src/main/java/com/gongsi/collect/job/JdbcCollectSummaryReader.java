package com.gongsi.collect.job;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** 적재된 테이블에서 그날 요약을 세는 조회 */
@Component
public class JdbcCollectSummaryReader implements CollectSummaryReader {

    private final JdbcTemplate jdbc;

    public JdbcCollectSummaryReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 1. 원문 페이지·권역  2. 판매 중 상품 (예적금·대출 + 신용대출)  3. 그날 이벤트  4. 정규화 오류
    @Override
    public CollectSummary read(LocalDate targetDate) {
        Date day = Date.valueOf(targetDate);
        int[] raw = jdbc.queryForObject(
                "SELECT count(*), count(DISTINCT fin_grp_no) FROM raw_snapshot WHERE fetched_on = ?",
                (rs, i) -> new int[] {rs.getInt(1), rs.getInt(2)}, day);
        Integer active = jdbc.queryForObject(
                "SELECT (SELECT count(*) FROM product WHERE is_active)"
                        + " + (SELECT count(*) FROM credit_loan_product WHERE is_active)", Integer.class);
        Map<String, Integer> events = new HashMap<>();
        jdbc.query("SELECT event_type, count(*) FROM change_event WHERE occurred_on = ? GROUP BY event_type",
                rs -> { events.put(rs.getString(1), rs.getInt(2)); }, day);
        Integer errors = jdbc.queryForObject(
                "SELECT count(*) FROM normalize_error WHERE fetched_on = ?", Integer.class, day);
        return new CollectSummary(raw[0], raw[1], active == null ? 0 : active,
                events.getOrDefault("NEW", 0), events.getOrDefault("CLOSED", 0),
                events.getOrDefault("RATE_UP", 0), events.getOrDefault("RATE_DOWN", 0),
                errors == null ? 0 : errors);
    }
}
