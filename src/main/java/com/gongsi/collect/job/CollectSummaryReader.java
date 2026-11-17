package com.gongsi.collect.job;

import java.time.LocalDate;

/** targetDate 수집 결과 요약을 읽는 쪽 */
@FunctionalInterface
public interface CollectSummaryReader {
    CollectSummary read(LocalDate targetDate);
}
