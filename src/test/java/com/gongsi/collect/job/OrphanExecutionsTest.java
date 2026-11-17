package com.gongsi.collect.job;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepExecution;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 고아 실행 선별과 FAILED 정리 */
class OrphanExecutionsTest {

    static final LocalDate DAY = LocalDate.of(2026, 11, 17);
    static final LocalDateTime BOOT = DAY.atTime(11, 0);

    static JobExecution execution(long id, BatchStatus status, LocalDateTime created) {
        var execution = new JobExecution(new JobInstance(1L, DailyCollectJobConfig.JOB_NAME), id,
                DailyCollectJobConfig.params(DAY));
        execution.setStatus(status);
        execution.setCreateTime(created);
        return execution;
    }

    static StepExecution step(JobExecution execution, long id, String name, BatchStatus status) {
        StepExecution step = execution.createStepExecution(name);
        step.setId(id);
        step.setStatus(status);
        return step;
    }

    @Test
    void 기동_전에_만든_running_실행만_고른다() {
        var started = execution(1, BatchStatus.STARTED, BOOT.minusMinutes(5));
        var stopping = execution(2, BatchStatus.STOPPING, BOOT.minusMinutes(3));
        var failed = execution(3, BatchStatus.FAILED, BOOT.minusMinutes(2));
        var mine = execution(4, BatchStatus.STARTED, BOOT.plusSeconds(1));

        assertThat(OrphanExecutions.select(List.of(mine, stopping, failed, started), BOOT))
                .containsExactly(started, stopping);
    }

    @Test
    void Job_과_미완료_Step_을_FAILED_로_닫는다() {
        var execution = execution(1, BatchStatus.STARTED, BOOT.minusMinutes(5));
        var fetch = step(execution, 10, "fetchStep", BatchStatus.COMPLETED);
        fetch.setExitStatus(ExitStatus.COMPLETED);
        var normalize = step(execution, 11, "normalizeStep", BatchStatus.STARTED);
        LocalDateTime now = BOOT.plusMinutes(1);

        assertThat(OrphanExecutions.stoppedStep(execution)).contains("normalizeStep");
        OrphanExecutions.markFailed(execution, now);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getEndTime()).isEqualTo(now);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
        assertThat(normalize.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(normalize.getEndTime()).isEqualTo(now);
        assertThat(fetch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(fetch.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
    }

    @Test
    void 정리된_실행은_재시도_대상_실패로_읽힌다() {
        var execution = execution(1, BatchStatus.STARTED, BOOT.minusMinutes(5));
        step(execution, 10, "diffStep", BatchStatus.STARTED);
        OrphanExecutions.markFailed(execution, BOOT.plusMinutes(1));

        CollectAttempt attempt = CollectAttempt.of(execution);

        assertThat(attempt.failed()).isTrue();
        assertThat(attempt.retryable()).isTrue();
        assertThat(attempt.failedStep()).isEqualTo("diffStep");
        assertThat(attempt.reason()).isEqualTo(OrphanExecutions.REASON);
    }

    @Test
    void Step_시작_전이면_멈춘_단계가_없다() {
        assertThat(OrphanExecutions.stoppedStep(execution(1, BatchStatus.STARTING, BOOT))).isEmpty();
    }
}
