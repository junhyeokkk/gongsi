package com.gongsi.collect.diff;

import com.gongsi.collect.normalize.model.NormalizedOption;

import java.math.BigDecimal;
import java.util.function.Function;

// 변동 감지 대상 금리 필드 - rate_history.field 값. rate_avg 는 null 이 많아 제외
public enum RateField {
    BASE_RATE("base_rate", NormalizedOption::baseRate),
    MAX_RATE("max_rate", NormalizedOption::maxRate),
    RATE_MIN("rate_min", NormalizedOption::rateMin),
    RATE_MAX("rate_max", NormalizedOption::rateMax);

    private final String column;
    private final Function<NormalizedOption, BigDecimal> getter;

    RateField(String column, Function<NormalizedOption, BigDecimal> getter) {
        this.column = column;
        this.getter = getter;
    }

    public String column() {
        return column;
    }

    public BigDecimal of(NormalizedOption option) {
        return getter.apply(option);
    }
}
