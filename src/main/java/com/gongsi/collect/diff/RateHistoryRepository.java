package com.gongsi.collect.diff;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

// rate_history·credit_rate_history 쓰기 - (옵션, 날짜, 필드) 충돌은 건너뛰어 같은 날 재실행해도 한 번만 남는다
@Repository
public class RateHistoryRepository {

    private static final String INSERT_SQL = """
            INSERT INTO %1$s (%2$s, changed_on, field, old_value, new_value)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (%2$s, changed_on, field) DO NOTHING
            """;

    private static final int[] ARG_TYPES = {Types.BIGINT, Types.DATE, Types.VARCHAR, Types.NUMERIC, Types.NUMERIC};

    private final JdbcTemplate jdbc;

    public RateHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 새로 들어간 행 수 반환
    public int insertAll(ProductFamily family, LocalDate changedOn, List<Row> rows) {
        Date day = Date.valueOf(changedOn);
        List<Object[]> args = rows.stream().map(r -> new Object[]{
                r.optionId(), day, r.field(), r.oldValue(), r.newValue()}).toList();
        String sql = INSERT_SQL.formatted(family.historyTable(), family.optionColumn());
        return Arrays.stream(jdbc.batchUpdate(sql, args, ARG_TYPES)).filter(n -> n > 0).sum();
    }

    // 이력 한 행 - field 는 컬럼 이름(base_rate, rate_grade_1 …)
    public record Row(long optionId, String field, BigDecimal oldValue, BigDecimal newValue) {
    }
}
