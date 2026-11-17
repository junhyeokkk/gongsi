package com.gongsi.collect.job;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

/** 일별 수집 배치 Job — fetch → normalize → diff → notify 순서로 실행 */
@Configuration
public class DailyCollectJobConfig {

    public static final String JOB_NAME = "dailyCollectJob";

    // targetDate 만 식별 파라미터 - 같은 날 다시 띄우면 실패한 Step 부터 이어서 돈다
    public static JobParameters params(LocalDate targetDate) {
        return new JobParametersBuilder().addString("targetDate", targetDate.toString()).toJobParameters();
    }

    @Bean
    public Job dailyCollectJob(
            JobRepository jobRepository,
            Step fetchStep,
            Step normalizeStep,
            Step diffStep,
            Step notifyStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(fetchStep)
                .next(normalizeStep)
                .next(diffStep)
                .next(notifyStep)
                .build();
    }
}
