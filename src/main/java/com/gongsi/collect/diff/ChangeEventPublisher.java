package com.gongsi.collect.diff;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

// change_event insert - uk_change_event_product·uk_change_event_credit 충돌은 건너뛰어 같은 날 재실행해도 한 번만 남는다
@Component
public class ChangeEventPublisher {

    // ON CONFLICT 대상은 COALESCE 표현식 부분 인덱스와 같은 모양이어야 한다
    private static final String INSERT_SQL = """
            INSERT INTO change_event (%1$s, %2$s, event_type, occurred_on, payload)
            VALUES (?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (%1$s, COALESCE(%2$s, 0), event_type, occurred_on)
                WHERE %1$s IS NOT NULL
            DO NOTHING
            """;

    private static final int[] ARG_TYPES = {Types.BIGINT, Types.BIGINT, Types.VARCHAR, Types.DATE, Types.VARCHAR};

    private final JdbcTemplate jdbc;

    public ChangeEventPublisher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 한 계열의 이벤트 적재, 새로 들어간 행 수 반환
    public int publish(ProductFamily family, LocalDate occurredOn, List<Event> events) {
        Date day = Date.valueOf(occurredOn);
        List<Object[]> args = events.stream().map(e -> new Object[]{
                e.productId(), e.optionId(), e.type().name(), day, e.payloadJson()}).toList();
        String sql = INSERT_SQL.formatted(family.productColumn(), family.optionColumn());
        return Arrays.stream(jdbc.batchUpdate(sql, args, ARG_TYPES)).filter(n -> n > 0).sum();
    }

    // change_event 한 행 - optionId 가 null 이면 상품 단위
    public record Event(long productId, Long optionId, ChangeEventType type, String payloadJson) {
    }
}
