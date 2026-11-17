package com.gongsi.collect.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gongsi.collect.fetch.FetchService;
import com.gongsi.collect.fetch.FetchSummary;
import com.gongsi.common.ProductType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 실패·고아 실행을 같은 targetDate 로 다시 띄우면 멈춘 Step 부터 이어 도는지 검증. Docker 가 있는 CI 에서만 실행
@SpringBootTest
@Testcontainers
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class DailyCollectJobRestartTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 10, 30);
    private static final LocalDate DAY2 = DAY1.plusDays(1);
    private static final LocalDate ORPHAN_DAY = LocalDate.of(2026, 11, 5);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path RAW_DIR = Path.of("survey/raw");
    private static final Map<ProductType, String> FIXTURES = Map.of(
            ProductType.DEP, "depositProductsSearch_020000_p1",
            ProductType.SAV, "savingProductsSearch_020000_p1",
            ProductType.MORT, "mortgageLoanProductsSearch_020000_p1",
            ProductType.RENT, "rentHouseLoanProductsSearch_020000_p1",
            ProductType.CRED, "creditLoanProductsSearch_020000_p1");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // 외부 API 대신 DAY2 원문을 적재
    @MockBean
    FetchService fetchService;

    @Autowired
    JobLauncher jobLauncher;

    @Autowired
    Job dailyCollectJob;

    @Autowired
    JobExplorer jobExplorer;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    OrphanExecutionCleaner cleaner;

    @Test
    void diffStep_실패_후_같은_날짜로_재실행하면_diffStep_부터_이어_돈다() throws Exception {
        // 1. 직전 수집일 원문, fetch 는 예금 상품 10개가 빠진 원문을 적재
        for (ProductType type : FIXTURES.keySet()) {
            insertRaw(DAY1, type, fixture(type));
        }
        when(fetchService.fetchAll(any())).thenAnswer(inv -> {
            for (ProductType type : FIXTURES.keySet()) {
                insertRaw(DAY2, type, type == ProductType.DEP ? withoutLastProducts(fixture(type), 10) : fixture(type));
            }
            return new FetchSummary(DAY2, List.of());
        });
        JobParameters params = new JobParametersBuilder().addString("targetDate", DAY2.toString()).toJobParameters();

        // 2. 첫 실행은 diffStep 급변 검사에서 실패
        JobExecution first = jobLauncher.run(dailyCollectJob, params);
        assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(statuses(first)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "fetchStep", BatchStatus.COMPLETED,
                "normalizeStep", BatchStatus.COMPLETED,
                "diffStep", BatchStatus.FAILED));

        // 3. 예금 원문을 온전한 것으로 바로잡고 같은 파라미터로 재실행
        jdbc.update("DELETE FROM raw_snapshot WHERE fetched_on = ? AND product_type = 'DEP'", DAY2);
        insertRaw(DAY2, ProductType.DEP, fixture(ProductType.DEP));
        JobExecution second = jobLauncher.run(dailyCollectJob, params);

        // 4. 같은 JobInstance 에서 diffStep·notifyStep 만 실행
        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(second.getJobInstance().getInstanceId()).isEqualTo(first.getJobInstance().getInstanceId());
        assertThat(statuses(second)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "diffStep", BatchStatus.COMPLETED,
                "notifyStep", BatchStatus.COMPLETED));
        assertThat(stepRuns(first)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "fetchStep", 1L, "normalizeStep", 1L, "diffStep", 2L, "notifyStep", 1L));
        verify(fetchService, times(1)).fetchAll(DAY2);
    }

    @Test
    void 재기동으로_STARTED_에_남은_실행을_정리하면_멈춘_Step_부터_이어_돈다() throws Exception {
        // 1. notifyStep 도중 앱이 죽어 STARTED 로 남은 실행
        JobParameters params = DailyCollectJobConfig.params(ORPHAN_DAY);
        JobExecution orphan = jobRepository.createJobExecution(DailyCollectJobConfig.JOB_NAME, params);
        orphan.setStatus(BatchStatus.STARTED);
        orphan.setStartTime(LocalDateTime.now());
        jobRepository.update(orphan);
        for (String name : List.of("fetchStep", "normalizeStep", "diffStep")) {
            addStep(orphan, name, BatchStatus.COMPLETED);
        }
        addStep(orphan, "notifyStep", BatchStatus.STARTED);
        assertThatThrownBy(() -> jobLauncher.run(dailyCollectJob, params))
                .isInstanceOf(JobExecutionAlreadyRunningException.class);

        // 2. 기동 시 정리 - Job·notifyStep 이 FAILED 로 닫힌다
        assertThat(cleaner.cleanup(LocalDateTime.now().plusSeconds(1)))
                .extracting(JobExecution::getId).containsExactly(orphan.getId());
        JobExecution cleaned = jobExplorer.getJobExecution(orphan.getId());
        assertThat(cleaned.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(cleaned.getEndTime()).isNotNull();
        assertThat(statuses(cleaned)).containsEntry("notifyStep", BatchStatus.FAILED)
                .containsEntry("diffStep", BatchStatus.COMPLETED);

        // 3. 같은 targetDate 재실행은 notifyStep 만 돌고 완료
        JobExecution rerun = jobLauncher.run(dailyCollectJob, params);
        assertThat(rerun.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(rerun.getJobInstance().getInstanceId()).isEqualTo(orphan.getJobInstance().getInstanceId());
        assertThat(statuses(rerun)).containsExactlyEntriesOf(Map.of("notifyStep", BatchStatus.COMPLETED));
    }

    private void addStep(JobExecution execution, String name, BatchStatus status) {
        StepExecution step = execution.createStepExecution(name);
        step.setStartTime(LocalDateTime.now());
        jobRepository.add(step);
        step.setStatus(status);
        if (status == BatchStatus.COMPLETED) {
            step.setExitStatus(ExitStatus.COMPLETED);
            step.setEndTime(LocalDateTime.now());
        }
        jobRepository.update(step);
    }

    private static Map<String, BatchStatus> statuses(JobExecution execution) {
        return execution.getStepExecutions().stream()
                .collect(Collectors.toMap(StepExecution::getStepName, StepExecution::getStatus));
    }

    // JobInstance 의 모든 실행에 걸친 Step 별 실행 횟수
    private Map<String, Long> stepRuns(JobExecution execution) {
        return jobExplorer.getJobExecutions(execution.getJobInstance()).stream()
                .map(e -> jobExplorer.getJobExecution(e.getId()))
                .flatMap(e -> e.getStepExecutions().stream())
                .collect(Collectors.groupingBy(StepExecution::getStepName, Collectors.counting()));
    }

    // 원문에서 뒤쪽 상품 n개와 그 옵션을 뺀다 - 급변 검사(10%·최소 5개)에 걸리게
    private static ObjectNode withoutLastProducts(ObjectNode root, int n) {
        ArrayNode bases = (ArrayNode) root.path("result").path("baseList");
        ArrayNode options = (ArrayNode) root.path("result").path("optionList");
        Set<String> gone = new HashSet<>();
        for (int i = 0; i < n; i++) {
            gone.add(bases.remove(bases.size() - 1).path("fin_prdt_cd").asText());
        }
        for (int i = options.size() - 1; i >= 0; i--) {
            if (gone.contains(options.get(i).path("fin_prdt_cd").asText())) {
                options.remove(i);
            }
        }
        return root;
    }

    private void insertRaw(LocalDate day, ProductType type, ObjectNode payload) {
        jdbc.update("""
                INSERT INTO raw_snapshot (fetched_on, product_type, fin_grp_no, page_no, payload, fetched_at)
                VALUES (?, ?, '020000', 1, ?::jsonb, now())
                """, day, type.name(), payload.toString());
    }

    private static ObjectNode fixture(ProductType type) throws IOException {
        return (ObjectNode) MAPPER.readTree(RAW_DIR.resolve(FIXTURES.get(type) + "_20261028.json").toFile());
    }
}
