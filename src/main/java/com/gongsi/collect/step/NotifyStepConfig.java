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

/** Step 4 — change_event 발행 완료 표시 (5주차 이후 구현) */
@Configuration
public class NotifyStepConfig {

    private static final Logger log = LoggerFactory.getLogger(NotifyStepConfig.class);

    @Bean
    public Step notifyStep(JobRepository jobRepository, PlatformTransactionManager txManager) {
        return new StepBuilder("notifyStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("notify 시작");
                    return RepeatStatus.FINISHED;
                }, txManager)
                .build();
    }
}
