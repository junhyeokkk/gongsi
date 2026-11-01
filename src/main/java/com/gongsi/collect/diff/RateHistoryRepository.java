package com.gongsi.collect.diff;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

// rate_history 쓰기 - uk_rate_history 충돌은 건너뛰어 같은 날 재실행해도 한 번만 남는다
@Repository
public class RateHistoryRepository {

    private static final String INSERT_SQL = """
            INSERT INTO rate_history (product_option_id, changed_on, field, old_value, new_value)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (product_option_id, changed_on, field) DO NOTHING
            """;

    private static final int[] ARG_TYPES = {Types.BIGINT, Types.DATE, Types.VARCHAR, Types.NUMERIC, Types.NUMERIC};

    private final JdbcTemplate jdbc;

    public RateHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 새로 들어간 행 수 반환
    public int insertAll(LocalDate changedOn, List<Row> rows) {
        Date day = Date.valueOf(changedOn);
        List<Object[]> args = rows.stream().map(r -> new Object[]{
                r.productOptionId(), day, r.field().column(), r.oldValue(), r.newValue()}).toList();
        return Arrays.stream(jdbc.batchUpdate(INSERT_SQL, args, ARG_TYPES)).filter(n -> n > 0).sum();
    }

    // rate_history 한 행
    public record Row(long productOptionId, RateField field, BigDecimal oldValue, BigDecimal newValue) {
    }
}
