package com.gongsi.collect.job;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/** 실행 직후 기록으로 만드는 슬랙 문구 (실패·복구·마감) */
public final class CollectAlertMessages {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private CollectAlertMessages() {
    }

    // 마지막 실행 결과에 맞는 문구 (첫 시도에 완료면 empty, nextRun null 이면 오늘 재시도 없음)
    public static Optional<String> afterRun(LocalDate targetDate, List<CollectAttempt> attempts,
                                            ZonedDateTime nextRun) {
        if (attempts.isEmpty()) {
            return Optional.empty();
        }
        CollectAttempt last = attempts.get(attempts.size() - 1);
        long failures = attempts.stream().filter(CollectAttempt::failed).count();
        if (last.completed()) {
            return failures == 0 ? Optional.empty()
                    : Optional.of("[gongsi] %s 수집 복구 - 실패 %d회 뒤 완료".formatted(targetDate, failures));
        }
        if (!last.failed()) {
            return Optional.empty();
        }
        String head = "[gongsi] %s 수집 실패 %d회째 (%s)\n사유: %s".formatted(
                targetDate, failures, nvl(last.failedStep(), "단계 모름"), nvl(last.reason(), "없음"));
        if (!last.retryable()) {
            return Optional.of(head + "\n재시도 안 함 - 원문 확인 필요");
        }
        return Optional.of(nextRun != null
                ? head + "\n다음 재시도 " + nextRun.format(HHMM)
                : head + "\n오늘 자동 재시도 끝 - 수동 실행 필요");
    }

    // Job 을 띄우지도 못했을 때
    public static String launchFailed(LocalDate targetDate, Exception e) {
        return "[gongsi] %s 수집 실행 못 함\n사유: %s".formatted(targetDate, e.getMessage());
    }

    private static String nvl(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
