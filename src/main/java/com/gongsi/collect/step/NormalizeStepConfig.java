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

/** Step 2 — raw_snapshot → product / product_option upsert (3·4주차 구현) */
@Configuration
public class NormalizeStepConfig {

    private static final Logger log = LoggerFactory.getLogger(NormalizeStepConfig.class);

    @Bean
    public Step normalizeStep(JobRepository jobRepository, PlatformTransactionManager txManager) {
        return new StepBuilder("normalizeStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("normalize 시작");
                    return RepeatStatus.FINISHED;
                }, txManager)
                .build();
    }
}
