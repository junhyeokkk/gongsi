package com.gongsi.collect.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** 기동 시 이전 JVM 이 남긴 running 실행을 FAILED 로 정리하고 알린다 (수동 실행 Job 보다 먼저) */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OrphanExecutionCleaner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrphanExecutionCleaner.class);

    private final JobExplorer jobExplorer;
    private final JobRepository jobRepository;
    private final CollectAlert alert;

    public OrphanExecutionCleaner(JobExplorer jobExplorer, JobRepository jobRepository, CollectAlert alert) {
        this.jobExplorer = jobExplorer;
        this.jobRepository = jobRepository;
        this.alert = alert;
    }

    @Override
    public void run(ApplicationArguments args) {
        Instant boot = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
        cleanup(LocalDateTime.ofInstant(boot, ZoneId.systemDefault()));
    }

    // bootTime 전에 만들어진 running 실행 정리 (Spring Batch 는 JVM 기본 시간대로 기록)
    List<JobExecution> cleanup(LocalDateTime bootTime) {
        List<JobExecution> orphans = OrphanExecutions.select(
                jobExplorer.findRunningJobExecutions(DailyCollectJobConfig.JOB_NAME), bootTime);
        for (JobExecution execution : orphans) {
            String step = OrphanExecutions.stoppedStep(execution).orElse(null);
            List<StepExecution> steps = OrphanExecutions.unfinishedSteps(execution);
            OrphanExecutions.markFailed(execution, LocalDateTime.now());
            steps.forEach(jobRepository::update);
            jobRepository.update(execution);
            LocalDate targetDate = targetDate(execution);
            log.warn("고아 실행 정리 executionId={} targetDate={} step={}", execution.getId(), targetDate, step);
            alert.send(CollectAlertMessages.orphanCleaned(targetDate, execution.getId(), step));
        }
        return orphans;
    }

    private static LocalDate targetDate(JobExecution execution) {
        String value = execution.getJobParameters().getString("targetDate");
        return value == null ? null : LocalDate.parse(value);
    }
}
