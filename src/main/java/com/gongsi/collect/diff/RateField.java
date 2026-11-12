package com.gongsi.collect.diff;

import java.math.BigDecimal;

// 변동 감지 대상 금리 필드 하나 - column 은 rate_history·credit_rate_history 의 field 값
public interface RateField<O> {

    String column();

    BigDecimal of(O option);
}
