package com.gongsi.collect.normalize;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// companySearch 원문이 company·company_area 로 멱등 적재되고 오염 레코드는 normalize_error 로 격리되는지 검증. Docker 가 있는 CI 에서만 실행
@SpringBootTest
@Testcontainers
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class CompanyNormalizeServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 31);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path RAW_DIR = Path.of("survey/raw");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    CompanyNormalizeService companyNormalizeService;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM normalize_error");
        jdbc.update("DELETE FROM company_area");
        jdbc.update("DELETE FROM company");
        jdbc.update("DELETE FROM raw_snapshot");
    }

    @Test
    void 권역별로_회사와_지역을_적재하고_오염_레코드는_격리() throws IOException {
        ObjectNode bank = fixture("020000");
        ArrayNode bases = (ArrayNode) bank.path("result").path("baseList");
        ObjectNode bad = bases.get(0).deepCopy();
        bad.put("fin_co_no", "9999999");
        bad.remove("kor_co_nm");
        bases.add(bad);
        ObjectNode orphan = bank.path("result").path("optionList").get(0).deepCopy();
        orphan.put("fin_co_no", "9999999");
        ((ArrayNode) bank.path("result").path("optionList")).add(orphan);
        insertRaw(DAY, "020000", bank);
        insertRaw(DAY, "030300", fixture("030300"));

        CompanyNormalizeService.Summary summary = companyNormalizeService.normalize(DAY);

        assertThat(countCompanies("020000")).isEqualTo(17);
        assertThat(countCompanies("030300")).isEqualTo(80);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_area", Integer.class)).isEqualTo(97 * 17);
        assertThat(summary.errors()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT reason FROM normalize_error WHERE product_type = 'COMPANY'", String.class))
                .containsExactlyInAnyOrder("MISSING_REQUIRED", "ORPHAN_OPTION");

        Map<String, Object> woori = jdbc.queryForMap(
                "SELECT company_name, call_tel, is_active FROM company WHERE fin_co_no = '0010001'");
        assertThat(woori).containsEntry("company_name", "우리은행").containsEntry("call_tel", "15885000")
                .containsEntry("is_active", true);
    }

    @Test
    void 같은_날짜를_다시_돌려도_행이_늘지_않음() throws IOException {
        insertRaw(DAY, "020000", fixture("020000"));

        companyNormalizeService.normalize(DAY);
        companyNormalizeService.normalize(DAY);

        assertThat(countCompanies("020000")).isEqualTo(17);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_area", Integer.class)).isEqualTo(17 * 17);
    }

    @Test
    void 다음날_사라진_회사는_비활성() throws IOException {
        insertRaw(DAY, "020000", fixture("020000"));
        companyNormalizeService.normalize(DAY);

        ObjectNode next = fixture("020000");
        ((ArrayNode) next.path("result").path("baseList")).remove(0);
        insertRaw(DAY.plusDays(1), "020000", next);
        CompanyNormalizeService.Summary summary = companyNormalizeService.normalize(DAY.plusDays(1));

        assertThat(summary.deactivated()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_active FROM company WHERE fin_co_no = '0010001'", Boolean.class))
                .isFalse();
    }

    @Test
    void 원문이_없으면_건너뛰고_기존_회사는_건드리지_않음() throws IOException {
        insertRaw(DAY, "020000", fixture("020000"));
        companyNormalizeService.normalize(DAY);

        CompanyNormalizeService.Summary summary = companyNormalizeService.normalize(DAY.plusDays(1));

        assertThat(summary.pages()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company WHERE is_active", Integer.class)).isEqualTo(17);
    }

    private void insertRaw(LocalDate day, String finGrpNo, ObjectNode payload) {
        jdbc.update("""
                INSERT INTO raw_snapshot (fetched_on, product_type, fin_grp_no, page_no, payload, fetched_at)
                VALUES (?, 'COMPANY', ?, 1, ?::jsonb, now())
                """, day, finGrpNo, payload.toString());
    }

    private static ObjectNode fixture(String finGrpNo) throws IOException {
        return (ObjectNode) MAPPER.readTree(RAW_DIR.resolve("companySearch_" + finGrpNo + "_p1_20261031.json").toFile());
    }

    private int countCompanies(String finGrpNo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM company WHERE fin_grp_no = ?", Integer.class, finGrpNo);
    }
}
