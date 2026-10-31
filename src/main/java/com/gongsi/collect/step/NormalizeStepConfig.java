package com.gongsi.collect.step;

import com.gongsi.collect.normalize.NormalizeService;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

// Step 2 - raw_snapshot → product / product_option upsert, 오염 레코드는 normalize_error 로 격리
@Configuration
public class NormalizeStepConfig {

    // 상품군 단위로 NormalizeService 가 커밋하므로 tasklet 은 DB 트랜잭션으로 묶지 않는다
    @Bean
    public Step normalizeStep(JobRepository jobRepository, NormalizeService normalizeService) {
        return new StepBuilder("normalizeStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    Object param = chunkContext.getStepContext().getJobParameters().get("targetDate");
                    if (param == null) {
                        throw new IllegalArgumentException("job parameter targetDate(yyyy-MM-dd) 가 필요하다");
                    }
                    normalizeService.normalizeAll(LocalDate.parse(param.toString()));
                    return RepeatStatus.FINISHED;
                }, new ResourcelessTransactionManager())
                .build();
    }
}
