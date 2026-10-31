package com.gongsi.collect.normalize;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gongsi.common.ProductType;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// raw_snapshot 원문이 product·product_option 으로 멱등 적재되고 오염 레코드는 normalize_error 로 격리되는지 검증. Docker 가 있는 CI 에서만 실행
@SpringBootTest
@Testcontainers
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class NormalizeServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 30);
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
    NormalizeService normalizeService;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM normalize_error");
        jdbc.update("DELETE FROM product_option");
        jdbc.update("DELETE FROM product");
        jdbc.update("DELETE FROM raw_snapshot");
    }

    @Test
    void 원문을_적재하고_오염_레코드는_사유별로_격리() throws IOException {
        insertDay(DAY, corruptedDeposit());

        List<NormalizeSummary> summaries = normalizeService.normalizeAll(DAY);

        assertThat(countProducts(ProductType.DEP)).isEqualTo(40);
        assertThat(countOptions(ProductType.DEP)).isEqualTo(163);
        assertThat(countProducts(ProductType.MORT)).isEqualTo(36);
        assertThat(countOptions(ProductType.MORT)).isEqualTo(111);
        assertThat(errorReasons()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "MISSING_REQUIRED", 1, "TYPE_MISMATCH", 1, "ORPHAN_OPTION", 1));
        NormalizeSummary dep = summaries.stream().filter(s -> s.type() == ProductType.DEP).findFirst().orElseThrow();
        assertThat(dep.products()).isEqualTo(40);
        assertThat(dep.options()).isEqualTo(163);
        assertThat(dep.errors()).isEqualTo(3);

        Map<String, Object> row = productRow("WR0001B");
        assertThat(row.get("is_active")).isEqualTo(true);
        assertThat(row.get("first_seen_on").toString()).isEqualTo(DAY.toString());
        assertThat(row.get("fin_grp_no")).isEqualTo("020000");
    }

    @Test
    void 같은_날짜를_다시_돌려도_행과_에러가_늘지_않음() throws IOException {
        insertDay(DAY, corruptedDeposit());

        normalizeService.normalizeAll(DAY);
        normalizeService.normalizeAll(DAY);

        assertThat(countProducts(ProductType.DEP)).isEqualTo(40);
        assertThat(countOptions(ProductType.DEP)).isEqualTo(163);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM normalize_error", Integer.class)).isEqualTo(3);
    }

    @Test
    void 다음날_사라진_상품은_비활성_first_seen_on_은_유지() throws IOException {
        insertDay(DAY, fixture("depositProductsSearch_020000_p1"));
        normalizeService.normalizeAll(DAY);

        ObjectNode next = fixture("depositProductsSearch_020000_p1");
        ArrayNode bases = (ArrayNode) next.path("result").path("baseList");
        bases.remove(0);
        LocalDate nextDay = DAY.plusDays(1);
        insertDay(nextDay, next);
        normalizeService.normalizeAll(nextDay);

        Map<String, Object> gone = productRow("WR0001B");
        assertThat(gone.get("is_active")).isEqualTo(false);
        assertThat(gone.get("last_seen_on").toString()).isEqualTo(DAY.toString());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM product_option o JOIN product p ON p.id = o.product_id
                WHERE p.product_code = 'WR0001B' AND p.product_type = 'DEP' AND o.is_active
                """, Integer.class)).isZero();

        Map<String, Object> kept = productRow(bases.get(0).path("fin_prdt_cd").asText());
        assertThat(kept.get("is_active")).isEqualTo(true);
        assertThat(kept.get("first_seen_on").toString()).isEqualTo(DAY.toString());
        assertThat(kept.get("last_seen_on").toString()).isEqualTo(nextDay.toString());
    }

    @Test
    void 원문이_없는_상품군이_있으면_실패() {
        assertThatThrownBy(() -> normalizeService.normalizeAll(DAY))
                .hasMessageContaining("raw_snapshot 없음");
    }

    // DEP 원문에 필수값 없는 base 1, 그 base 의 옵션(고아) 1, 기간이 문자인 옵션 1 을 섞는다
    private static ObjectNode corruptedDeposit() throws IOException {
        ObjectNode root = fixture("depositProductsSearch_020000_p1");
        ArrayNode bases = (ArrayNode) root.path("result").path("baseList");
        ArrayNode options = (ArrayNode) root.path("result").path("optionList");
        ObjectNode badBase = bases.get(0).deepCopy();
        badBase.put("fin_prdt_cd", "BAD0001");
        badBase.remove("fin_prdt_nm");
        bases.add(badBase);
        ObjectNode orphan = options.get(0).deepCopy();
        orphan.put("fin_prdt_cd", "BAD0001");
        options.add(orphan);
        ((ObjectNode) options.get(0)).put("save_trm", "x");
        return root;
    }

    private void insertDay(LocalDate day, ObjectNode deposit) throws IOException {
        insertRaw(day, ProductType.DEP, deposit);
        insertRaw(day, ProductType.SAV, fixture("savingProductsSearch_020000_p1"));
        insertRaw(day, ProductType.MORT, fixture("mortgageLoanProductsSearch_020000_p1"));
        insertRaw(day, ProductType.RENT, fixture("rentHouseLoanProductsSearch_020000_p1"));
    }

    private void insertRaw(LocalDate day, ProductType type, ObjectNode payload) {
        jdbc.update("""
                INSERT INTO raw_snapshot (fetched_on, product_type, fin_grp_no, page_no, payload, fetched_at)
                VALUES (?, ?, '020000', 1, ?::jsonb, now())
                """, day, type.name(), payload.toString());
    }

    private static ObjectNode fixture(String fileStem) throws IOException {
        return (ObjectNode) MAPPER.readTree(RAW_DIR.resolve(fileStem + "_20261028.json").toFile());
    }

    private int countProducts(ProductType type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE product_type = ?", Integer.class, type.name());
    }

    private int countOptions(ProductType type) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM product_option o JOIN product p ON p.id = o.product_id WHERE p.product_type = ?
                """, Integer.class, type.name());
    }

    private Map<String, Object> productRow(String productCode) {
        return jdbc.queryForMap("""
                SELECT is_active, first_seen_on, last_seen_on, fin_grp_no FROM product
                WHERE product_code = ? AND product_type = 'DEP'
                """, productCode);
    }

    private Map<String, Integer> errorReasons() {
        Map<String, Integer> m = new HashMap<>();
        jdbc.query("SELECT reason, COUNT(*)::int AS n FROM normalize_error GROUP BY reason",
                rs -> {
                    m.put(rs.getString("reason"), rs.getInt("n"));
                });
        return m;
    }
}
