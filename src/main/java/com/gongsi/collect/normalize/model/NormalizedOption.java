package com.gongsi.collect.normalize.model;

import java.math.BigDecimal;

// optionList 레코드 하나를 product_option 컬럼과 1:1 로 옮긴 값 - productKey 로 부모 상품을 찾는다
public record NormalizedOption(
        ProductKey productKey,
        String optionKey,
        Short saveTerm,
        String rateType,
        String reserveType,
        BigDecimal baseRate,
        BigDecimal maxRate,
        String mortgageType,
        String repayType,
        String lendRateType,
        BigDecimal rateMin,
        BigDecimal rateMax,
        BigDecimal rateAvg) {
}
