package com.gongsi.collect.normalize.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gongsi.collect.normalize.model.CreditProductKey;
import com.gongsi.collect.normalize.model.NormalizedCreditOption;
import com.gongsi.collect.normalize.model.NormalizedCreditProduct;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 신용대출 원문 레코드가 credit_loan_* 값으로 옮겨지고 오염 레코드는 사유별로 걸러지는지 검증
class CreditNormalizerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path RAW_DIR = Path.of("survey/raw");
    private static final CreditNormalizer NORMALIZER = CreditNormalizer.INSTANCE;

    @Test
    void 같은_상품코드라도_신용상품유형이_다르면_다른_상품() throws IOException {
        List<CreditProductKey> keys = new ArrayList<>();
        for (JsonNode b : list("020000", "baseList")) {
            NormalizedCreditProduct p = NORMALIZER.normalizeBase(b);
            if (p.key().instCode().equals("0010016") && p.key().productCode().equals("WR0002F")) {
                keys.add(p.key());
            }
        }

        assertThat(keys).containsExactlyInAnyOrder(
                new CreditProductKey("0010016", "WR0002F", "2"), new CreditProductKey("0010016", "WR0002F", "3"));
    }

    @Test
    void base_를_컬럼으로_옮기고_신용평가사는_대문자로() throws IOException {
        ObjectNode b = (ObjectNode) list("020000", "baseList").get(0).deepCopy();
        b.put("cb_name", "kcb").put("dcls_end_day", "99991231");

        NormalizedCreditProduct p = NORMALIZER.normalizeBase(b);

        assertThat(p.instName()).isNotBlank();
        assertThat(p.cbName()).isEqualTo("KCB");
        assertThat(p.disclosureMonth()).isEqualTo("202610");
        assertThat(p.disclosureStartDate()).isAfter(LocalDate.of(2026, 1, 1));
        assertThat(p.disclosureEndDate()).isNull();
    }

    @Test
    void 옵션은_금리종류별_구간금리와_평균() throws IOException {
        NormalizedCreditOption o = null;
        for (JsonNode raw : list("020000", "optionList")) {
            if (raw.path("fin_co_no").asText().equals("0010016") && raw.path("fin_prdt_cd").asText().equals("WR0002F")
                    && raw.path("crdt_prdt_type").asText().equals("2")
                    && raw.path("crdt_lend_rate_type").asText().equals("A")) {
                o = NORMALIZER.normalizeOption(raw);
            }
        }

        assertThat(o).isNotNull();
        assertThat(NORMALIZER.optionKeyOf(o)).isEqualTo("A");
        assertThat(o.productKey()).isEqualTo(new CreditProductKey("0010016", "WR0002F", "2"));
        assertThat(o.rateGrade1()).isEqualByComparingTo("6.68");
        assertThat(o.rateGrade10()).isNull();
        assertThat(o.rateAvg()).isEqualByComparingTo(new BigDecimal("6.77"));
    }

    @Test
    void 정의_밖_코드값은_UNKNOWN_ENUM() throws IOException {
        ObjectNode b = (ObjectNode) list("020000", "baseList").get(0).deepCopy();
        b.put("crdt_prdt_type", "9");
        ObjectNode o = (ObjectNode) list("020000", "optionList").get(0).deepCopy();
        o.put("crdt_lend_rate_type", "Z");

        assertReason(() -> NORMALIZER.normalizeBase(b), NormalizeErrorReason.UNKNOWN_ENUM);
        assertReason(() -> NORMALIZER.normalizeOption(o), NormalizeErrorReason.UNKNOWN_ENUM);
    }

    @Test
    void 격리된_레코드도_자연키가_읽히면_상품을_가리킴() throws IOException {
        ObjectNode b = (ObjectNode) list("020000", "baseList").get(0).deepCopy();
        b.remove("fin_prdt_nm");

        assertReason(() -> NORMALIZER.normalizeBase(b), NormalizeErrorReason.MISSING_REQUIRED);
        assertThat(NORMALIZER.rawKey(b)).contains(new CreditProductKey(
                b.path("fin_co_no").asText(), b.path("fin_prdt_cd").asText(), b.path("crdt_prdt_type").asText()));
    }

    @Test
    void 실제_원문_전체가_오류_없이_정규화됨() throws IOException {
        for (String finGrpNo : new String[]{"020000", "030300"}) {
            list(finGrpNo, "baseList").forEach(NORMALIZER::normalizeBase);
            list(finGrpNo, "optionList").forEach(NORMALIZER::normalizeOption);
        }
    }

    private static void assertReason(Runnable call, NormalizeErrorReason reason) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(NormalizeException.class,
                e -> assertThat(e.reason()).isEqualTo(reason));
    }

    private static JsonNode list(String finGrpNo, String list) throws IOException {
        return MAPPER.readTree(RAW_DIR.resolve("creditLoanProductsSearch_" + finGrpNo + "_p1_20261028.json").toFile())
                .path("result").path(list);
    }
}
