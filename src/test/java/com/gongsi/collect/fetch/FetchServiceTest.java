package com.gongsi.collect.fetch;

import com.gongsi.common.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** FetchService 가 페이지를 돌며 raw_snapshot 에 멱등 적재하는지 검증. Docker 가 있는 CI 에서만 실행 */
@SpringBootTest
@Testcontainers
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class FetchServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 30);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockBean
    FssOpenFinanceClient client;

    @Autowired
    FetchService fetchService;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM raw_snapshot");
        when(client.fetchPage(any(), anyString(), anyInt())).thenReturn(page(1, 1, 10));
        when(client.fetchCompanyPage(anyString(), anyInt())).thenReturn(page(1, 1, 5));
    }

    @Test
    void max_page_no_까지_모든_페이지를_적재() {
        when(client.fetchPage(ProductType.DEP, "030300", 1)).thenReturn(page(1, 3, 250));
        when(client.fetchPage(ProductType.DEP, "030300", 2)).thenReturn(page(2, 3, 250));
        when(client.fetchPage(ProductType.DEP, "030300", 3)).thenReturn(page(3, 3, 250));

        FetchSummary summary = fetchService.fetchAll(DAY);

        assertThat(countRows(ProductType.DEP, "030300")).isEqualTo(3);
        assertThat(countRows(ProductType.DEP, "020000")).isEqualTo(1);
        assertThat(summary.hasFailures()).isFalse();
        assertThat(summary.result(ProductType.DEP).pages()).isEqualTo(4);
        assertThat(summary.result(ProductType.DEP).totalCount()).isEqualTo(260);
        assertThat(summary.result(RawSnapshotRepository.COMPANY).totalCount()).isEqualTo(10);
        assertThat(countRows(RawSnapshotRepository.COMPANY, "030300")).isEqualTo(1);
    }

    @Test
    void 같은_날짜로_다시_돌리면_행이_늘지_않고_payload_만_갱신() {
        fetchService.fetchAll(DAY);
        when(client.fetchPage(ProductType.SAV, "020000", 1)).thenReturn(page(1, 1, 99));

        fetchService.fetchAll(DAY);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM raw_snapshot", Integer.class)).isEqualTo(12);
        Integer total = jdbc.queryForObject("""
                SELECT (payload -> 'result' ->> 'total_count')::int FROM raw_snapshot
                WHERE fetched_on = ? AND product_type = 'SAV' AND fin_grp_no = '020000' AND page_no = 1
                """, Integer.class, DAY);
        assertThat(total).isEqualTo(99);
    }

    @Test
    void 금융회사_수집이_실패하면_요약에_실패로_남김() {
        when(client.fetchCompanyPage("030300", 1)).thenThrow(new FssApiException("timeout"));

        FetchSummary summary = fetchService.fetchAll(DAY);

        assertThat(summary.hasFailures()).isTrue();
        assertThat(summary.result(RawSnapshotRepository.COMPANY).failedGroups()).containsExactly("030300");
        assertThat(countRows(RawSnapshotRepository.COMPANY, "020000")).isEqualTo(1);
    }

    @Test
    void 한_상품군이_실패해도_나머지는_적재하고_실패를_요약에_남김() {
        when(client.fetchPage(eq(ProductType.MORT), anyString(), anyInt()))
                .thenThrow(new FssApiException("timeout"));
        when(client.fetchPage(ProductType.RENT, "030300", 1)).thenReturn("""
                {"result":{"err_cd":"010","err_msg":"미등록 인증키"}}
                """);

        FetchSummary summary = fetchService.fetchAll(DAY);

        assertThat(summary.hasFailures()).isTrue();
        assertThat(summary.result(ProductType.MORT).failedGroups()).containsExactly("020000", "030300");
        assertThat(summary.result(ProductType.RENT).failedGroups()).containsExactly("030300");
        assertThat(countRows(ProductType.MORT, "020000")).isZero();
        assertThat(countRows(ProductType.RENT, "020000")).isEqualTo(1);
        assertThat(countRows(ProductType.CRED, "030300")).isEqualTo(1);
    }

    private int countRows(ProductType type, String finGrpNo) {
        return countRows(type.name(), finGrpNo);
    }

    private int countRows(String dataset, String finGrpNo) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM raw_snapshot WHERE fetched_on = ? AND product_type = ? AND fin_grp_no = ?",
                Integer.class, DAY, dataset, finGrpNo);
    }

    private static String page(int nowPage, int maxPage, int totalCount) {
        return """
                {"result":{"err_cd":"000","err_msg":"정상","total_count":%d,"max_page_no":%d,"now_page_no":%d,
                "baseList":[],"optionList":[]}}
                """.formatted(totalCount, maxPage, nowPage);
    }
}
