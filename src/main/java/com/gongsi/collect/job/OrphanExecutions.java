package com.gongsi.collect.job;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 재기동으로 주인을 잃고 running 으로 남은 실행을 고르고 FAILED 로 닫는 규칙 */
public final class OrphanExecutions {

    static final String REASON = "앱 재기동으로 중단 - 기동 시 정리";

    private OrphanExecutions() {
    }

    // running 이고 이 JVM 기동 전에 만들어진 실행 (EC2 1대라 전부 고아)
    public static List<JobExecution> select(Collection<JobExecution> executions, LocalDateTime bootTime) {
        return executions.stream()
                .filter(e -> e.getStatus().isRunning())
                .filter(e -> e.getCreateTime() != null && e.getCreateTime().isBefore(bootTime))
                .sorted(Comparator.comparing(JobExecution::getId))
                .toList();
    }

    // Job 과 미완료 Step 을 FAILED 로 닫는다 [같은 targetDate 재실행 시 그 Step 부터 재시작]
    public static void markFailed(JobExecution execution, LocalDateTime now) {
        ExitStatus failed = ExitStatus.FAILED.addExitDescription(REASON);
        for (StepExecution step : unfinishedSteps(execution)) {
            step.setStatus(BatchStatus.FAILED);
            step.setExitStatus(failed);
            step.setEndTime(now);
        }
        execution.setStatus(BatchStatus.FAILED);
        execution.setExitStatus(failed);
        execution.setEndTime(now);
    }

    public static List<StepExecution> unfinishedSteps(JobExecution execution) {
        return execution.getStepExecutions().stream()
                .filter(s -> s.getStatus().isRunning())
                .sorted(Comparator.comparing(StepExecution::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    // 실행이 멈춘 Step 이름 (Step 시작 전이면 empty)
    public static Optional<String> stoppedStep(JobExecution execution) {
        return unfinishedSteps(execution).stream().map(StepExecution::getStepName).findFirst();
    }
}
