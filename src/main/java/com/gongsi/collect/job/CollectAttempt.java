package com.gongsi.collect.job;

import com.gongsi.collect.step.NoRetryExitListener;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/** dailyCollectJob 실행 한 번의 결과 (실패 단계·종료 코드·사유) */
public record CollectAttempt(BatchStatus status, Instant endTime, String failedStep, String exitCode, String reason) {

    private static final int REASON_MAX = 300; // 알림에 싣는 사유 길이

    public boolean completed() {
        return status == BatchStatus.COMPLETED;
    }

    public boolean running() {
        return status.isRunning();
    }

    public boolean failed() {
        return status.isUnsuccessful() || status == BatchStatus.STOPPED;
    }

    public boolean retryable() {
        return !NoRetryExitListener.EXIT_CODE.equals(exitCode);
    }

    // 실패한 Step 의 종료 코드·사유 첫 줄을 뽑는다
    public static CollectAttempt of(JobExecution execution) {
        Optional<StepExecution> failed = execution.getStepExecutions().stream()
                .filter(s -> s.getStatus().isUnsuccessful())
                .findFirst();
        return new CollectAttempt(
                execution.getStatus(),
                toInstant(execution.getEndTime()),
                failed.map(StepExecution::getStepName).orElse(null),
                failed.map(s -> s.getExitStatus().getExitCode()).orElse(execution.getExitStatus().getExitCode()),
                firstLine(failed.map(s -> s.getExitStatus().getExitDescription())
                        .orElse(execution.getExitStatus().getExitDescription())));
    }

    // Spring Batch 는 JVM 기본 시간대로 기록한다
    private static Instant toInstant(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String line = text.strip().lines().findFirst().orElse("");
        return line.length() > REASON_MAX ? line.substring(0, REASON_MAX) + "…" : line;
    }
}
