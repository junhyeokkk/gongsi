package com.gongsi.collect.step;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Step 1 — finlife API 호출 후 raw_snapshot 저장 (2주차 구현) */
@Configuration
public class FetchStepConfig {

    private static final Logger log = LoggerFactory.getLogger(FetchStepConfig.class);

    @Bean
    public Step fetchStep(JobRepository jobRepository, PlatformTransactionManager txManager) {
        return new StepBuilder("fetchStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    var targetDate = chunkContext.getStepContext()
                            .getJobParameters().get("targetDate");
                    log.info("fetch 시작: targetDate={}", targetDate);
                    return RepeatStatus.FINISHED;
                }, txManager)
                .build();
    }
}
