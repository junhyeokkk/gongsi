package com.gongsi.collect.job;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** 배치 메타 테이블에서 읽는 실행 기록 [재기동돼도 이어서 판단] */
@Component
public class JobExplorerAttemptHistory implements AttemptHistory {

    private final JobExplorer jobExplorer;

    public JobExplorerAttemptHistory(JobExplorer jobExplorer) {
        this.jobExplorer = jobExplorer;
    }

    @Override
    public List<CollectAttempt> of(LocalDate targetDate) {
        JobInstance instance = jobExplorer.getJobInstance(
                DailyCollectJobConfig.JOB_NAME, DailyCollectJobConfig.params(targetDate));
        if (instance == null) {
            return List.of();
        }
        return jobExplorer.getJobExecutions(instance).stream()
                .sorted(Comparator.comparing(JobExecution::getId))
                .map(CollectAttempt::of)
                .toList();
    }
}
