package com.gongsi.collect.job;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 일별 수집 배치 Job — fetch → normalize → diff → notify 순서로 실행 */
@Configuration
public class DailyCollectJobConfig {

    @Bean
    public Job dailyCollectJob(
            JobRepository jobRepository,
            Step fetchStep,
            Step normalizeStep,
            Step diffStep,
            Step notifyStep) {
        return new JobBuilder("dailyCollectJob", jobRepository)
                .start(fetchStep)
                .next(normalizeStep)
                .next(diffStep)
                .next(notifyStep)
                .build();
    }
}
