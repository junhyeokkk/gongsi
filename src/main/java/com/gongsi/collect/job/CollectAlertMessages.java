package com.gongsi.collect.job;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 실행 직후 기록으로 만드는 슬랙 문구 (완료·복구·실패·중단·마감) - 제목 한 줄 + 항목 목록 */
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
            String title = failures == 0
                    ? title(":white_check_mark:", "수집 완료", targetDate, null)
                    : title(":large_green_circle:", "수집 복구", targetDate, "실패 %d회 뒤 완료".formatted(failures));
            return Optional.of(summary == null ? title : title + "\n" + summaryItems(summary));
        }
        if (!last.failed()) {
            return Optional.of(title(":grey_question:", "수집 상태 확인 필요", targetDate, last.status().name()));
        }
        String count = "%d회째".formatted(failures);
        String cause = item("단계", nvl(last.failedStep(), "모름")) + "\n" + item("사유", nvl(last.reason(), "없음"));
        if (!last.retryable()) {
            return Optional.of(title(":octagonal_sign:", "수집 중단", targetDate, count) + "\n" + cause
                    + "\n" + item("조치", "재시도 안 함 - 원문 확인 필요"));
        }
        if (nextRun == null) {
            return Optional.of(title(":red_circle:", "수집 실패", targetDate, count) + "\n" + cause
                    + "\n" + item("조치", "오늘 자동 재시도 끝 - 수동 실행 필요"));
        }
        return Optional.of(title(":warning:", "수집 실패", targetDate, count) + "\n" + cause
                + "\n" + item("다음", nextRun.format(HHMM) + " 재시도"));
    }

    // 원문·상품·금리·오류 네 항목
    static String summaryItems(CollectSummary s) {
        return String.join("\n",
                item("원문", "%d페이지 · %d권역".formatted(s.pages(), s.groups())),
                item("상품", "판매 중 %,d (신규 %d · 종료 %d)".formatted(s.activeProducts(), s.newProducts(), s.closedProducts())),
                item("금리", "인상 %d · 인하 %d".formatted(s.rateUp(), s.rateDown())),
                item("오류", "정규화 %d".formatted(s.normalizeErrors())));
    }

    // Job 을 띄우지도 못했을 때
    public static String launchFailed(LocalDate targetDate, Exception e) {
        return title(":x:", "수집 실행 못 함", targetDate, null) + "\n" + item("사유", nvl(e.getMessage(), "없음"));
    }

    // 재기동으로 끊긴 실행을 FAILED 로 정리했을 때 (step null 이면 Step 시작 전)
    public static String orphanCleaned(LocalDate targetDate, Long executionId, String step) {
        return title(":recycle:", "중단된 수집 정리", targetDate, "재기동") + "\n"
                + item("실행", "#" + executionId + " " + nvl(step, "Step 시작 전") + " 에서 끊김") + "\n"
                + item("조치", "FAILED 로 정리 - 백오프 뒤 그 단계부터 자동 재시도");
    }

    // 예) :warning: *gongsi 수집 실패* · 2026-11-17 (1회째)
    private static String title(String emoji, String what, LocalDate targetDate, String note) {
        List<String> parts = new ArrayList<>(List.of(emoji, "*gongsi " + what + "*", "·", String.valueOf(targetDate)));
        if (note != null) {
            parts.add("(" + note + ")");
        }
        return String.join(" ", parts);
    }

    // 예) • *사유*  timeout [슬랙 특수문자 이스케이프]
    private static String item(String label, String value) {
        return "• *" + label + "*  " + escape(value);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String nvl(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
