package com.gongsi.collect.step;

import com.gongsi.collect.fetch.FetchService;
import com.gongsi.collect.fetch.FetchSummary;
import com.gongsi.collect.fetch.FssApiException;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

/** Step 1 — finlife API 호출 후 raw_snapshot 저장 */
@Configuration
public class FetchStepConfig {

    // 페이지마다 바로 커밋되도록 tasklet 을 DB 트랜잭션으로 묶지 않는다. 재실행은 UPSERT 로 멱등
    @Bean
    public Step fetchStep(JobRepository jobRepository, FetchService fetchService) {
        return new StepBuilder("fetchStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    Object param = chunkContext.getStepContext().getJobParameters().get("targetDate");
                    if (param == null) {
                        throw new IllegalArgumentException("job parameter targetDate(yyyy-MM-dd) 가 필요하다");
                    }
                    FetchSummary summary = fetchService.fetchAll(LocalDate.parse(param.toString()));
                    if (summary.hasFailures()) {
                        // 빠진 상품군이 있으면 뒤 단계가 상품 소멸로 오판하므로 Step 을 실패시킨다
                        throw new FssApiException("일부 상품군 수집 실패: " + summary.results());
                    }
                    return RepeatStatus.FINISHED;
                }, new ResourcelessTransactionManager())
                .build();
    }
}
