package com.gongsi.collect.diff;

import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** rate_history / credit_rate_history 쓰기. uk_rate_history 로 멱등 */
@Repository
public class RateHistoryRepository {

    // TODO 4주차: JdbcTemplate 주입
    // TODO 4주차: insertRateChange(productOptionId, changedOn, field, oldValue, newValue)
    //   - ON CONFLICT (product_option_id, changed_on, field) DO NOTHING (멱등)
    // TODO 4주차: insertCreditRateChange(creditLoanOptionId, changedOn, field, oldValue, newValue)
    //   - 신용대출은 테이블 분리

    public void insertRateChange(long productOptionId, LocalDate changedOn, String field,
                                 BigDecimal oldValue, BigDecimal newValue) {
        throw new UnsupportedOperationException("4주차 구현 예정");
    }
}
