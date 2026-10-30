package com.gongsi.collect.job;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** 스케줄러가 서울 날짜로 Job 을 띄우고, 켜짐 설정일 때만 등록되는지 검증 */
class DailyCollectSchedulerTest {

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

    @Test
    void 서버_시계가_UTC_여도_서울_날짜를_targetDate_로_넘긴다() {
        // UTC 10-30 15:30 = KST 10-31 00:30
        Clock utc = Clock.fixed(Instant.parse("2026-10-30T15:30:00Z"), ZoneOffset.UTC);
        List<JobParameters> launched = new ArrayList<>();
        JobLauncher launcher = (j, params) -> {
            launched.add(params);
            JobExecution execution = new JobExecution(1L);
            execution.setStatus(BatchStatus.COMPLETED);
            return execution;
        };

        new DailyCollectScheduler(launcher, job, utc).launch();

        assertThat(launched).singleElement()
                .extracting(p -> p.getString("targetDate"))
                .isEqualTo("2026-10-31");
    }

    @Test
    void 이미_완료된_날짜면_예외없이_건너뛴다() {
        JobLauncher launcher = (j, params) -> {
            throw new JobInstanceAlreadyCompleteException("done");
        };

        assertThatCode(() -> new DailyCollectScheduler(launcher, job, Clock.systemUTC()).launch())
                .doesNotThrowAnyException();
    }

    @Test
    void 설정을_켜야만_등록된다() {
        JobLauncher launcher = (j, params) -> new JobExecution(1L);
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(JobLauncher.class, () -> launcher)
                .withBean("dailyCollectJob", Job.class, () -> job)
                .withUserConfiguration(DailyCollectScheduler.class)
                .withPropertyValues("collect.schedule.cron=0 0 9 * * *");

        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(DailyCollectScheduler.class));
        runner.withPropertyValues("collect.schedule.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(DailyCollectScheduler.class));
    }
}
