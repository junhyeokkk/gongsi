package com.gongsi.collect.job;

/** 하루 수집 결과 요약 (원문·상품·금리 변동·정규화 오류) */
public record CollectSummary(int pages, int groups, int activeProducts, int newProducts, int closedProducts,
                             int rateUp, int rateDown, int normalizeErrors) {
}
