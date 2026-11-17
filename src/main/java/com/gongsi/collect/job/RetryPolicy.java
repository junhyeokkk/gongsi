package com.gongsi.collect.job;

import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/** 오늘 실행 기록으로 지금 Job 을 띄울지 정하는 재시도 규칙 */
public final class RetryPolicy {

    // 실패 n회째 뒤 대기 (넷째부터 60분)
    static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(10), Duration.ofMinutes(30), Duration.ofMinutes(60));

    public enum Action { RUN, DONE, RUNNING, NO_RETRY, WAIT }

    public record Decision(Action action, Instant retryAfter) {
        static Decision of(Action action) {
            return new Decision(action, null);
        }
    }

    private RetryPolicy() {
    }

    // 기록 없음 → 실행, 완료 → skip, 재시도 불가 → skip, 실패 n회 → 마지막 종료 + 대기 뒤 실행
    public static Decision decide(List<CollectAttempt> attempts, Instant now) {
        if (attempts.isEmpty()) {
            return Decision.of(Action.RUN);
        }
        if (attempts.stream().anyMatch(CollectAttempt::completed)) {
            return Decision.of(Action.DONE);
        }
        CollectAttempt last = attempts.get(attempts.size() - 1);
        if (last.running()) {
            return Decision.of(Action.RUNNING);
        }
        if (!last.retryable()) {
            return Decision.of(Action.NO_RETRY);
        }
        Optional<Instant> retryAfter = retryAfter(attempts);
        if (retryAfter.isPresent() && now.isBefore(retryAfter.get())) {
            return new Decision(Action.WAIT, retryAfter.get());
        }
        return Decision.of(Action.RUN);
    }

    // 마지막 실패 종료 + 대기 (실패가 아니거나 재시도 불가면 empty)
    public static Optional<Instant> retryAfter(List<CollectAttempt> attempts) {
        if (attempts.isEmpty()) {
            return Optional.empty();
        }
        CollectAttempt last = attempts.get(attempts.size() - 1);
        if (!last.failed() || !last.retryable() || last.endTime() == null) {
            return Optional.empty();
        }
        long failures = attempts.stream().filter(CollectAttempt::failed).count();
        Duration wait = BACKOFF.get((int) Math.min(failures, BACKOFF.size()) - 1);
        return Optional.of(last.endTime().plus(wait));
    }

    // 대기가 끝난 뒤 첫 스케줄 시각 (그날 안에 없으면 empty = 오늘 자동 재시도 끝)
    public static Optional<ZonedDateTime> nextRun(List<CollectAttempt> attempts, CronExpression cron,
                                                  LocalDate targetDate, ZoneId zone) {
        return retryAfter(attempts)
                .map(t -> cron.next(t.atZone(zone).minusNanos(1)))
                .filter(next -> next.toLocalDate().equals(targetDate));
    }
}
