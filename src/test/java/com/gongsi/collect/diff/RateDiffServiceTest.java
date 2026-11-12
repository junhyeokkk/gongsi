package com.gongsi.collect.diff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gongsi.collect.normalize.NormalizeService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 직전 수집일 원문과 비교해 rate_history·change_event 가 멱등 적재되는지 검증. Docker 가 있는 CI 에서만 실행
@SpringBootTest
@Testcontainers
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class RateDiffServiceTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 10, 30);
    private static final LocalDate DAY2 = DAY1.plusDays(1);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path RAW_DIR = Path.of("survey/raw");
    private static final String CHANGED = "WR0001B";
    private static final String CREDIT = "creditLoanProductsSearch_020000_p1";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    RateDiffService diffService;

    @Autowired
    NormalizeService normalizeService;

    @Autowired
    JdbcTemplate jdbc;

    private int closedRateRows;

    @BeforeEach
    void setUp() throws IOException {
        jdbc.update("DELETE FROM change_event");
        jdbc.update("DELETE FROM rate_history");
        jdbc.update("DELETE FROM credit_rate_history");
        jdbc.update("DELETE FROM normalize_error");
        jdbc.update("DELETE FROM credit_loan_option");
        jdbc.update("DELETE FROM credit_loan_product");
        jdbc.update("DELETE FROM product_option");
        jdbc.update("DELETE FROM product");
        jdbc.update("DELETE FROM raw_snapshot");
        insertDay(DAY1, fixture());
        insertDay(DAY2, changedDeposit());
    }

    @Test
    void 직전_원문_대비_변동을_이력과_이벤트로_적재() {
        normalizeService.normalizeAll(DAY1);
        normalizeService.normalizeAll(DAY2);

        List<DiffSummary> summaries = diffService.detectChanges(DAY2);

        assertExpectedChanges();
        DiffSummary dep = summaries.stream().filter(s -> s.type() == ProductType.DEP).findFirst().orElseThrow();
        assertThat(dep.eventsInserted()).isEqualTo(5);
        assertThat(dep.unresolved()).isZero();
        assertThat(summaries).filteredOn(s -> s.type() != ProductType.DEP)
                .allSatisfy(s -> assertThat(s.detected()).isEmpty());
        JsonNode payload = payload("RATE_UP");
        assertThat(payload.path("changes").get(0).path("field").asText()).isEqualTo("max_rate");
        assertThat(payload.path("changes").get(0).path("new").decimalValue()).isEqualByComparingTo("2.9");
    }

    @Test
    void 같은_날짜를_다시_돌려도_행이_늘지_않음() {
        normalizeService.normalizeAll(DAY1);
        normalizeService.normalizeAll(DAY2);

        diffService.detectChanges(DAY2);
        List<DiffSummary> again = diffService.detectChanges(DAY2);

        assertExpectedChanges();
        assertThat(again).allSatisfy(s -> {
            assertThat(s.rateRowsInserted()).isZero();
            assertThat(s.eventsInserted()).isZero();
        });
    }

    @Test
    void 정규화를_역순으로_돌려도_결과가_같음() {
        // product_option 금리는 마지막으로 정규화한 DAY1 값이 남는다
        normalizeService.normalizeAll(DAY2);
        normalizeService.normalizeAll(DAY1);

        diffService.detectChanges(DAY2);

        assertExpectedChanges();
    }

    @Test
    void 직전_원문이_없는_첫날은_비교하지_않음() {
        normalizeService.normalizeAll(DAY1);

        List<DiffSummary> summaries = diffService.detectChanges(DAY1);

        assertThat(summaries).allSatisfy(s -> assertThat(s.detected()).isEmpty());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM change_event", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rate_history", Integer.class)).isZero();
    }

    @Test
    void 오늘_격리된_상품은_사라짐으로_보지_않음() throws IOException {
        ObjectNode deposit = fixture();
        ((ObjectNode) deposit.path("result").path("baseList").get(1)).remove("fin_prdt_nm");
        replaceDeposit(DAY2, deposit);
        normalizeService.normalizeAll(DAY1);
        normalizeService.normalizeAll(DAY2);

        diffService.detectChanges(DAY2);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM change_event", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rate_history", Integer.class)).isZero();
    }

    @Test
    void 상품이_한꺼번에_사라지면_원문_누락으로_보고_실패() throws IOException {
        ObjectNode deposit = fixture();
        ArrayNode bases = (ArrayNode) deposit.path("result").path("baseList");
        ArrayNode options = (ArrayNode) deposit.path("result").path("optionList");
        Set<String> gone = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            gone.add(bases.remove(bases.size() - 1).path("fin_prdt_cd").asText());
        }
        for (int i = options.size() - 1; i >= 0; i--) {
            if (gone.contains(options.get(i).path("fin_prdt_cd").asText())) {
                options.remove(i);
            }
        }
        replaceDeposit(DAY2, deposit);
        normalizeService.normalizeAll(DAY1);
        normalizeService.normalizeAll(DAY2);

        assertThatThrownBy(() -> diffService.detectChanges(DAY2)).hasMessageContaining("상품 급변");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM change_event", Integer.class)).isZero();
    }

    @Test
    void 신용대출_금리_변동은_credit_계열로_적재() throws IOException {
        jdbc.update("DELETE FROM raw_snapshot WHERE fetched_on = ? AND product_type = 'CRED'", DAY2);
        insertRaw(DAY2, ProductType.CRED, changedCredit());
        normalizeService.normalizeAll(DAY1);
        normalizeService.normalizeAll(DAY2);

        List<DiffSummary> summaries = diffService.detectChanges(DAY2);
        diffService.detectChanges(DAY2);

        DiffSummary cred = summaries.stream().filter(s -> s.type() == ProductType.CRED).findFirst().orElseThrow();
        assertThat(cred.unresolved()).isZero();
        assertThat(cred.eventsInserted()).isEqualTo(3);
        assertThat(jdbc.queryForList("""
                SELECT e.event_type || ':' || o.lend_rate_type FROM change_event e
                JOIN credit_loan_product p ON p.id = e.credit_loan_product_id
                JOIN credit_loan_option o ON o.id = e.credit_loan_option_id
                WHERE e.product_id IS NULL AND p.inst_code = '0010016' AND p.product_code = 'WR0002F'
                    AND p.credit_product_type = '2'
                ORDER BY 1
                """, String.class)).containsExactly("OPTION_REMOVED:D", "RATE_DOWN:A", "RATE_UP:A");
        // A 의 1구간·평균 2행 + 사라진 D 의 금리 4행(1·4·6구간, 평균)
        assertThat(jdbc.queryForList("SELECT field FROM credit_rate_history WHERE changed_on = ? ORDER BY field",
                String.class, DAY2)).containsExactly(
                "rate_avg", "rate_avg", "rate_grade_1", "rate_grade_1", "rate_grade_4", "rate_grade_6");
        JsonNode down = MAPPER.readTree(jdbc.queryForObject("""
                SELECT payload::text FROM change_event WHERE credit_loan_product_id IS NOT NULL AND event_type = 'RATE_DOWN'
                """, String.class));
        assertThat(down.path("changes").get(0).path("field").asText()).isEqualTo("rate_avg");
    }

    @Test
    void 원문이_없는_날짜면_실패() {
        assertThatThrownBy(() -> diffService.detectChanges(DAY2.plusDays(1)))
                .hasMessageContaining("raw_snapshot 없음");
    }

    // RATE_UP·RATE_DOWN(같은 옵션) 1, OPTION_REMOVED 1, 옵션 NEW 1, CLOSED 1
    private void assertExpectedChanges() {
        assertThat(eventCounts()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "RATE_UP", 1, "RATE_DOWN", 1, "OPTION_REMOVED", 1, "NEW", 1, "CLOSED", 1));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM change_event WHERE event_type = 'CLOSED' AND product_option_id IS NULL
                """, Integer.class)).isEqualTo(1);
        // 금리 변동 2 + 사라진 옵션 2 + 새 옵션 2 + 사라진 상품의 옵션들
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rate_history WHERE changed_on = ?",
                Integer.class, DAY2)).isEqualTo(6 + closedRateRows);
    }

    // DAY1 원문에서 WR0001B 첫 옵션 금리 변경, 둘째 옵션 삭제, 99개월 옵션 추가, 둘째 상품 통째로 삭제
    private ObjectNode changedDeposit() throws IOException {
        ObjectNode root = fixture();
        ArrayNode bases = (ArrayNode) root.path("result").path("baseList");
        ArrayNode options = (ArrayNode) root.path("result").path("optionList");
        String closed = bases.get(1).path("fin_prdt_cd").asText();
        bases.remove(1);

        List<Integer> changedIdx = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).path("fin_prdt_cd").asText().equals(CHANGED)) {
                changedIdx.add(i);
            }
        }
        ObjectNode first = (ObjectNode) options.get(changedIdx.get(0));
        first.put("intr_rate", 2.5).put("intr_rate2", 2.9);
        ObjectNode added = first.deepCopy().put("save_trm", "99");
        options.remove(changedIdx.get(1).intValue());
        options.add(added);

        for (int i = options.size() - 1; i >= 0; i--) {
            JsonNode o = options.get(i);
            if (o.path("fin_prdt_cd").asText().equals(closed)) {
                closedRateRows += (o.path("intr_rate").isNumber() ? 1 : 0) + (o.path("intr_rate2").isNumber() ? 1 : 0);
                options.remove(i);
            }
        }
        return root;
    }

    // 0010016/WR0002F 마이너스한도의 대출금리(A) 1구간 인상·평균 인하, 가감조정금리(D) 삭제
    private static ObjectNode changedCredit() throws IOException {
        ObjectNode root = fixture(CREDIT);
        ArrayNode options = (ArrayNode) root.path("result").path("optionList");
        for (int i = options.size() - 1; i >= 0; i--) {
            ObjectNode o = (ObjectNode) options.get(i);
            boolean target = o.path("fin_co_no").asText().equals("0010016")
                    && o.path("fin_prdt_cd").asText().equals("WR0002F") && o.path("crdt_prdt_type").asText().equals("2");
            if (target && o.path("crdt_lend_rate_type").asText().equals("A")) {
                o.put("crdt_grad_1", 6.90).put("crdt_grad_avg", 6.70);
            } else if (target && o.path("crdt_lend_rate_type").asText().equals("D")) {
                options.remove(i);
            }
        }
        return root;
    }

    private void insertDay(LocalDate day, ObjectNode deposit) throws IOException {
        insertRaw(day, ProductType.DEP, deposit);
        insertRaw(day, ProductType.SAV, fixture("savingProductsSearch_020000_p1"));
        insertRaw(day, ProductType.MORT, fixture("mortgageLoanProductsSearch_020000_p1"));
        insertRaw(day, ProductType.RENT, fixture("rentHouseLoanProductsSearch_020000_p1"));
        insertRaw(day, ProductType.CRED, fixture(CREDIT));
    }

    private void replaceDeposit(LocalDate day, ObjectNode deposit) {
        jdbc.update("DELETE FROM raw_snapshot WHERE fetched_on = ? AND product_type = 'DEP'", day);
        insertRaw(day, ProductType.DEP, deposit);
    }

    private void insertRaw(LocalDate day, ProductType type, ObjectNode payload) {
        jdbc.update("""
                INSERT INTO raw_snapshot (fetched_on, product_type, fin_grp_no, page_no, payload, fetched_at)
                VALUES (?, ?, '020000', 1, ?::jsonb, now())
                """, day, type.name(), payload.toString());
    }

    private static ObjectNode fixture() throws IOException {
        return fixture("depositProductsSearch_020000_p1");
    }

    private static ObjectNode fixture(String fileStem) throws IOException {
        return (ObjectNode) MAPPER.readTree(RAW_DIR.resolve(fileStem + "_20261028.json").toFile());
    }

    private Map<String, Integer> eventCounts() {
        Map<String, Integer> m = new HashMap<>();
        jdbc.query("SELECT event_type, COUNT(*)::int AS n FROM change_event GROUP BY event_type",
                rs -> {
                    m.put(rs.getString("event_type"), rs.getInt("n"));
                });
        return m;
    }

    private JsonNode payload(String eventType) {
        try {
            return MAPPER.readTree(jdbc.queryForObject(
                    "SELECT payload::text FROM change_event WHERE event_type = ?", String.class, eventType));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
