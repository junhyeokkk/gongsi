package com.gongsi.collect.step;

import com.gongsi.collect.diff.MassChangeException;
import com.gongsi.collect.job.CollectAttempt;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;

import static org.assertj.core.api.Assertions.assertThat;

/** 급변 실패만 FAILED_NO_RETRY 로 남고, 실행 기록에서 재시도 불가로 읽힌다 */
class NoRetryExitListenerTest {

    private static StepExecution failedStep(JobExecution job, Throwable e) {
        StepExecution step = job.createStepExecution("diffStep");
        step.setStatus(BatchStatus.FAILED);
        step.setExitStatus(ExitStatus.FAILED.addExitDescription(e));
        step.addFailureException(e);
        return step;
    }

    @Test
    void 급변_예외면_재시도_불가_코드를_남긴다() {
        JobExecution job = new JobExecution(1L);
        job.setStatus(BatchStatus.FAILED);
        StepExecution step = failedStep(job, new MassChangeException("상품 급변 - 원문 누락 의심"));

        step.setExitStatus(new NoRetryExitListener().afterStep(step));

        assertThat(step.getExitStatus().getExitCode()).isEqualTo(NoRetryExitListener.EXIT_CODE);
        CollectAttempt attempt = CollectAttempt.of(job);
        assertThat(attempt.retryable()).isFalse();
        assertThat(attempt.failedStep()).isEqualTo("diffStep");
        assertThat(attempt.reason()).contains("상품 급변");
    }

    @Test
    void 다른_예외면_그대로_재시도한다() {
        JobExecution job = new JobExecution(2L);
        job.setStatus(BatchStatus.FAILED);
        StepExecution step = failedStep(job, new IllegalStateException("timeout"));

        step.setExitStatus(new NoRetryExitListener().afterStep(step));

        assertThat(step.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
        assertThat(CollectAttempt.of(job).retryable()).isTrue();
    }
}
