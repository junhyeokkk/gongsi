package com.gongsi.collect.job;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static com.gongsi.collect.job.RetryPolicyTest.failed;
import static com.gongsi.collect.job.RetryPolicyTest.kst;
import static org.assertj.core.api.Assertions.assertThat;

/** 스케줄러가 실행 기록으로 띄울지 정하고, 결과를 알리고, 켜짐 설정일 때만 등록되는지 검증 */
class DailyCollectSchedulerTest {

    private static final String CRON = "0 */10 9-17 * * *";
    private static final CollectSummary SUMMARY = new CollectSummary(29, 4, 1057, 0, 0, 89, 6, 0);

    // 스케줄러는 Job 을 넘기기만 하므로 실행되지 않는 빈 Job 으로 충분
    private final Job job = new Job() {
        @Override
        public String getName() {
            return "dailyCollectJob";
        }

        @Override
        public void execute(JobExecution execution) {
        }
    };

    private final List<CollectAttempt> attempts = new ArrayList<>();
    private final List<JobParameters> launched = new ArrayList<>();
    private final List<String> alerts = new ArrayList<>();

    // 실행하면 주어진 결과를 기록에 쌓는 launcher
    private JobLauncher launcherAppending(CollectAttempt result) {
        return (j, params) -> {
            launched.add(params);
            attempts.add(result);
            return new JobExecution(1L);
        };
    }

    private DailyCollectScheduler scheduler(JobLauncher launcher, Instant now) {
        return new DailyCollectScheduler(launcher, job, d -> List.copyOf(attempts), alerts::add, d -> SUMMARY, CRON,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void 서버_시계가_UTC_여도_서울_날짜를_targetDate_로_넘긴다() {
        // UTC 10-30 15:30 = KST 10-31 00:30
        var done = new CollectAttempt(BatchStatus.COMPLETED, Instant.parse("2026-10-30T15:31:00Z"), null, "COMPLETED", null);

        scheduler(launcherAppending(done), Instant.parse("2026-10-30T15:30:00Z")).launch();

        assertThat(launched).singleElement()
                .extracting(p -> p.getString("targetDate"))
                .isEqualTo("2026-10-31");
        assertThat(alerts).singleElement().asString().startsWith("[gongsi] 2026-10-31 수집 완료\n원문 29페이지");
    }

    @Test
    void 대기_중이면_띄우지_않는다() {
        attempts.add(failed(kst(9, 5)));

        scheduler(launcherAppending(null), kst(9, 10)).launch();

        assertThat(launched).isEmpty();
        assertThat(alerts).isEmpty();
    }

    @Test
    void 실패하면_다음_재시도_시각을_알린다() {
        scheduler(launcherAppending(failed(kst(9, 5))), kst(9, 0)).launch();

        assertThat(alerts).singleElement().asString().contains("수집 실패 1회째 (fetchStep)", "다음 재시도 09:20");
    }

    @Test
    void 실패_뒤_완료되면_복구를_알린다() {
        attempts.add(failed(kst(9, 5)));
        var done = new CollectAttempt(BatchStatus.COMPLETED, kst(9, 25), null, "COMPLETED", null);

        scheduler(launcherAppending(done), kst(9, 20)).launch();

        assertThat(alerts).singleElement().asString().startsWith("[gongsi] 2026-11-17 수집 복구 - 실패 1회 뒤 완료\n원문");
    }

    @Test
    void 요약_조회가_실패해도_완료를_알린다() {
        var done = new CollectAttempt(BatchStatus.COMPLETED, kst(9, 1), null, "COMPLETED", null);
        CollectSummaryReader broken = d -> {
            throw new IllegalStateException("db down");
        };

        new DailyCollectScheduler(launcherAppending(done), job, d -> List.copyOf(attempts), alerts::add, broken, CRON,
                Clock.fixed(kst(9, 0), ZoneOffset.UTC)).launch();

        assertThat(alerts).containsExactly("[gongsi] 2026-11-17 수집 완료");
    }

    @Test
    void 띄우지_못하면_알린다() {
        JobLauncher launcher = (j, params) -> {
            throw new JobRestartException("restart blocked");
        };

        scheduler(launcher, kst(9, 0)).launch();

        assertThat(alerts).singleElement().asString().contains("수집 실행 못 함", "restart blocked");
    }

    @Test
    void 설정을_켜야만_등록된다() {
        JobLauncher launcher = (j, params) -> new JobExecution(1L);
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(JobLauncher.class, () -> launcher)
                .withBean("dailyCollectJob", Job.class, () -> job)
                .withBean(AttemptHistory.class, () -> d -> List.of())
                .withBean(CollectAlert.class, () -> text -> { })
                .withBean(CollectSummaryReader.class, () -> d -> SUMMARY)
                .withUserConfiguration(DailyCollectScheduler.class)
                .withPropertyValues("collect.schedule.cron=" + CRON);

        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(DailyCollectScheduler.class));
        runner.withPropertyValues("collect.schedule.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(DailyCollectScheduler.class));
    }
}
