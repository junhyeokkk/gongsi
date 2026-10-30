package com.gongsi.collect.fetch;

import com.gongsi.common.ProductType;

import java.time.LocalDate;
import java.util.List;

/** 하루치 수집 결과. 상품군별 적재 페이지 수·total_count 합·실패한 금융권 코드 */
public record FetchSummary(LocalDate targetDate, List<TypeResult> results) {

    public FetchSummary {
        results = List.copyOf(results);
    }

    /** 상품군 하나의 수집 결과 */
    public record TypeResult(ProductType type, int pages, int totalCount, List<String> failedGroups) {
        public TypeResult {
            failedGroups = List.copyOf(failedGroups);
        }
    }

    public boolean hasFailures() {
        return results.stream().anyMatch(r -> !r.failedGroups().isEmpty());
    }

    public TypeResult result(ProductType type) {
        return results.stream().filter(r -> r.type() == type).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("수집 결과 없음: " + type));
    }
}
