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

    // 마지막 실행 결과에 맞는 문구 (nextRun null 이면 오늘 재시도 없음, summary null 이면 요약 생략)
    public static Optional<String> afterRun(LocalDate targetDate, List<CollectAttempt> attempts,
                                            ZonedDateTime nextRun, CollectSummary summary) {
        if (attempts.isEmpty()) {
            return Optional.empty();
        }
        CollectAttempt last = attempts.get(attempts.size() - 1);
        long failures = attempts.stream().filter(CollectAttempt::failed).count();
        if (last.completed()) {
            String done = failures == 0 ? "[gongsi] %s 수집 완료".formatted(targetDate)
                    : "[gongsi] %s 수집 복구 - 실패 %d회 뒤 완료".formatted(targetDate, failures);
            return Optional.of(summary == null ? done : done + "\n" + summaryLines(summary));
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

    // 원문·상품 / 금리 변동·오류 두 줄
    static String summaryLines(CollectSummary s) {
        return "원문 %d페이지 · %d권역 / 판매 중 상품 %,d (신규 %d, 종료 %d)\n금리 변동 인상 %d · 인하 %d / 정규화 오류 %d"
                .formatted(s.pages(), s.groups(), s.activeProducts(), s.newProducts(), s.closedProducts(),
                        s.rateUp(), s.rateDown(), s.normalizeErrors());
    }

    // Job 을 띄우지도 못했을 때
    public static String launchFailed(LocalDate targetDate, Exception e) {
        return "[gongsi] %s 수집 실행 못 함\n사유: %s".formatted(targetDate, e.getMessage());
    }

    private static String nvl(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
