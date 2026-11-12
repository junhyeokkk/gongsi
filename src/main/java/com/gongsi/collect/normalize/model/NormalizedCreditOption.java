package com.gongsi.collect.normalize.model;

import java.math.BigDecimal;

// 신용대출 optionList 레코드 하나 - 금리 종류(A~D) 한 행에 신용점수 구간별 금리 8개와 평균
public record NormalizedCreditOption(
        CreditProductKey productKey,
        String lendRateType,
        BigDecimal rateGrade1,
        BigDecimal rateGrade4,
        BigDecimal rateGrade5,
        BigDecimal rateGrade6,
        BigDecimal rateGrade10,
        BigDecimal rateGrade11,
        BigDecimal rateGrade12,
        BigDecimal rateGrade13,
        BigDecimal rateAvg) {
}
