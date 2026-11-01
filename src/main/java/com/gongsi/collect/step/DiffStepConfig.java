package com.gongsi.collect.step;

import com.gongsi.collect.diff.RateDiffService;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

// Step 3 - 직전 수집일 대비 금리 변동 감지 → rate_history + change_event 적재
@Configuration
public class DiffStepConfig {

    // 서비스가 전 상품군을 한 트랜잭션으로 커밋하므로 tasklet 은 DB 트랜잭션으로 묶지 않는다
    @Bean
    public Step diffStep(JobRepository jobRepository, RateDiffService rateDiffService) {
        return new StepBuilder("diffStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    Object param = chunkContext.getStepContext().getJobParameters().get("targetDate");
                    if (param == null) {
                        throw new IllegalArgumentException("job parameter targetDate(yyyy-MM-dd) 가 필요하다");
                    }
                    rateDiffService.detectChanges(LocalDate.parse(param.toString()));
                    return RepeatStatus.FINISHED;
                }, new ResourcelessTransactionManager())
                .build();
    }
}
