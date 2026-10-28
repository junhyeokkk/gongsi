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

/** Step 3 — 전일 대비 금리 변동 감지 → rate_history + change_event 적재 (5주차 구현) */
@Configuration
public class DiffStepConfig {

    private static final Logger log = LoggerFactory.getLogger(DiffStepConfig.class);

    @Bean
    public Step diffStep(JobRepository jobRepository, PlatformTransactionManager txManager) {
        return new StepBuilder("diffStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("diff 시작");
                    return RepeatStatus.FINISHED;
                }, txManager)
                .build();
    }
}
