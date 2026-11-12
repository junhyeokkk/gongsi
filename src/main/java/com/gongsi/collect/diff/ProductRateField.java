package com.gongsi.collect.diff;

import com.gongsi.collect.normalize.model.NormalizedOption;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

// 예금·적금·주담대·전세 비교 필드 - rate_avg 는 null 이 많아 제외
public enum ProductRateField implements RateField<NormalizedOption> {
    BASE_RATE("base_rate", NormalizedOption::baseRate),
    MAX_RATE("max_rate", NormalizedOption::maxRate),
    RATE_MIN("rate_min", NormalizedOption::rateMin),
    RATE_MAX("rate_max", NormalizedOption::rateMax);

    public static final List<ProductRateField> ALL = List.of(values());

    private final String column;
    private final Function<NormalizedOption, BigDecimal> getter;

    ProductRateField(String column, Function<NormalizedOption, BigDecimal> getter) {
        this.column = column;
        this.getter = getter;
    }

    @Override
    public String column() {
        return column;
    }

    @Override
    public BigDecimal of(NormalizedOption option) {
        return getter.apply(option);
    }
}
