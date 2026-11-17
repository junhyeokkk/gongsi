package com.gongsi.collect.job;

import java.time.LocalDate;
import java.util.List;

/** targetDate 의 dailyCollectJob 실행 기록 (오래된 순) */
@FunctionalInterface
public interface AttemptHistory {
    List<CollectAttempt> of(LocalDate targetDate);
}
