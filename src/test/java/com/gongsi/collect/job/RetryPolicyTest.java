package com.gongsi.collect.job;

import com.gongsi.collect.job.RetryPolicy.Action;
import com.gongsi.collect.step.NoRetryExitListener;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 실행 기록 → 실행·대기·중단 판단과 다음 재시도 시각 */
class RetryPolicyTest {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final LocalDate DAY = LocalDate.of(2026, 11, 17);
    static final CronExpression CRON = CronExpression.parse("0 */10 9-17 * * *");

    static Instant kst(int hour, int minute) {
        return DAY.atTime(hour, minute).atZone(SEOUL).toInstant();
    }

    static CollectAttempt failed(Instant end) {
        return new CollectAttempt(BatchStatus.FAILED, end, "fetchStep", "FAILED", "timeout");
    }

    @Test
    void 기록이_없으면_실행한다() {
        assertThat(RetryPolicy.decide(List.of(), kst(9, 0)).action()).isEqualTo(Action.RUN);
    }

    @Test
    void 완료가_있으면_건너뛴다() {
        var done = new CollectAttempt(BatchStatus.COMPLETED, kst(9, 5), null, "COMPLETED", null);
        assertThat(RetryPolicy.decide(List.of(failed(kst(9, 1)), done), kst(9, 30)).action()).isEqualTo(Action.DONE);
    }

    @Test
    void 실행_중이면_건너뛴다() {
        var running = new CollectAttempt(BatchStatus.STARTED, null, null, "UNKNOWN", null);
        assertThat(RetryPolicy.decide(List.of(running), kst(9, 30)).action()).isEqualTo(Action.RUNNING);
    }

    @Test
    void 급변_실패는_재시도하지_않는다() {
        var mass = new CollectAttempt(BatchStatus.FAILED, kst(9, 5), "diffStep", NoRetryExitListener.EXIT_CODE, "급변");
        assertThat(RetryPolicy.decide(List.of(mass), kst(12, 0)).action()).isEqualTo(Action.NO_RETRY);
        assertThat(RetryPolicy.retryAfter(List.of(mass))).isEmpty();
    }

    @Test
    void 첫_실패는_10분_뒤에_다시_돈다() {
        List<CollectAttempt> attempts = List.of(failed(kst(9, 5)));

        var waiting = RetryPolicy.decide(attempts, kst(9, 10));
        assertThat(waiting.action()).isEqualTo(Action.WAIT);
        assertThat(waiting.retryAfter()).isEqualTo(kst(9, 15));
        assertThat(RetryPolicy.decide(attempts, kst(9, 20)).action()).isEqualTo(Action.RUN);
    }

    @Test
    void 대기는_10분_30분_60분_이후_60분으로_늘어난다() {
        assertThat(RetryPolicy.retryAfter(List.of(failed(kst(9, 0)), failed(kst(9, 20)))))
                .contains(kst(9, 50));
        assertThat(RetryPolicy.retryAfter(List.of(failed(kst(9, 0)), failed(kst(9, 20)), failed(kst(10, 0)))))
                .contains(kst(11, 0));
        assertThat(RetryPolicy.retryAfter(List.of(failed(kst(9, 0)), failed(kst(9, 20)), failed(kst(10, 0)),
                failed(kst(11, 0))))).contains(kst(12, 0));
    }

    @Test
    void 다음_재시도는_대기가_끝난_뒤_첫_스케줄_시각이다() {
        assertThat(RetryPolicy.nextRun(List.of(failed(kst(9, 5))), CRON, DAY, SEOUL))
                .map(ZonedDateTime::toLocalTime).hasValueSatisfying(t -> assertThat(t).hasToString("09:20"));
        assertThat(RetryPolicy.nextRun(List.of(failed(kst(9, 10))), CRON, DAY, SEOUL))
                .map(ZonedDateTime::toLocalTime).hasValueSatisfying(t -> assertThat(t).hasToString("09:20"));
    }

    @Test
    void 대기가_오늘_마지막_스케줄을_넘기면_재시도가_없다() {
        List<CollectAttempt> attempts = List.of(failed(kst(9, 0)), failed(kst(9, 20)), failed(kst(16, 55)));
        assertThat(RetryPolicy.nextRun(attempts, CRON, DAY, SEOUL)).isEmpty();
    }
}
