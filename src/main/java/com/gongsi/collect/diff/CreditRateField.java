package com.gongsi.collect.diff;

import com.gongsi.collect.normalize.model.NormalizedCreditOption;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

// 신용대출 비교 필드 - 구간별 금리 8개와 평균. crdt_grad_avg 는 원문에서 null 이 없어 포함
public enum CreditRateField implements RateField<NormalizedCreditOption> {
    RATE_GRADE_1("rate_grade_1", NormalizedCreditOption::rateGrade1),
    RATE_GRADE_4("rate_grade_4", NormalizedCreditOption::rateGrade4),
    RATE_GRADE_5("rate_grade_5", NormalizedCreditOption::rateGrade5),
    RATE_GRADE_6("rate_grade_6", NormalizedCreditOption::rateGrade6),
    RATE_GRADE_10("rate_grade_10", NormalizedCreditOption::rateGrade10),
    RATE_GRADE_11("rate_grade_11", NormalizedCreditOption::rateGrade11),
    RATE_GRADE_12("rate_grade_12", NormalizedCreditOption::rateGrade12),
    RATE_GRADE_13("rate_grade_13", NormalizedCreditOption::rateGrade13),
    RATE_AVG("rate_avg", NormalizedCreditOption::rateAvg);

    public static final List<CreditRateField> ALL = List.of(values());

    private final String column;
    private final Function<NormalizedCreditOption, BigDecimal> getter;

    CreditRateField(String column, Function<NormalizedCreditOption, BigDecimal> getter) {
        this.column = column;
        this.getter = getter;
    }

    @Override
    public String column() {
        return column;
    }

    @Override
    public BigDecimal of(NormalizedCreditOption option) {
        return getter.apply(option);
    }
}
