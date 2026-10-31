package com.gongsi.collect.normalize.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gongsi.collect.normalize.model.NormalizedOption;
import com.gongsi.collect.normalize.model.NormalizedProduct;
import com.gongsi.collect.normalize.model.ProductKey;
import com.gongsi.common.ProductType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 상품군별 Normalizer 가 원문 레코드를 컬럼 단위로 옮기고 오염 레코드를 사유별로 거르는지 검증
class NormalizerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path RAW_DIR = Path.of("survey/raw");

    @Test
    void 예금_base_를_컬럼으로_옮기고_99991231_종료일은_null() throws IOException {
        ObjectNode base = (ObjectNode) first("depositProductsSearch_020000_p1", "baseList");
        base.put("dcls_end_day", "99991231");

        NormalizedProduct p = Normalizer.of(ProductType.DEP).normalizeBase(base);

        assertThat(p.key()).isEqualTo(new ProductKey("0010001", "WR0001B"));
        assertThat(p.instName()).isEqualTo("우리은행");
        assertThat(p.productName()).isEqualTo("WON플러스예금");
        assertThat(p.joinDeny()).isEqualTo((short) 1);
        assertThat(p.disclosureMonth()).isEqualTo("202610");
        assertThat(p.disclosureStartDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(p.disclosureEndDate()).isNull();
        assertThat(p.submittedAt()).isEqualTo(LocalDateTime.of(2026, 10, 20, 14, 20));
        assertThat(p.specialCondition()).isEqualTo("해당사항 없음");
        assertThat(p.loanLimitNote()).isNull();
    }

    @Test
    void 대출_base_는_대출_전용_컬럼을_채움() throws IOException {
        NormalizedProduct p = Normalizer.of(ProductType.MORT)
                .normalizeBase(first("mortgageLoanProductsSearch_020000_p1", "baseList"));

        assertThat(p.loanLimitNote()).isEqualTo("LTV 70%");
        assertThat(p.delinquencyRateNote()).contains("적용금리");
        assertThat(p.maxLimit()).isNull();
        assertThat(p.disclosureEndDate()).isEqualTo(LocalDate.of(2027, 10, 19));
    }

    @Test
    void 예금_옵션키는_기간_금리유형() throws IOException {
        NormalizedOption o = Normalizer.of(ProductType.DEP)
                .normalizeOption(first("depositProductsSearch_020000_p1", "optionList"));

        assertThat(o.productKey()).isEqualTo(new ProductKey("0010001", "WR0001B"));
        assertThat(o.optionKey()).isEqualTo("1|S");
        assertThat(o.saveTerm()).isEqualTo((short) 1);
        assertThat(o.baseRate()).isEqualByComparingTo("2.7");
        assertThat(o.maxRate()).isEqualByComparingTo("2.7");
        assertThat(o.reserveType()).isNull();
    }

    @Test
    void 적금_옵션키는_적립유형까지() throws IOException {
        NormalizedOption o = Normalizer.of(ProductType.SAV)
                .normalizeOption(first("savingProductsSearch_020000_p1", "optionList"));

        assertThat(o.optionKey()).isEqualTo("12|S|S");
        assertThat(o.maxRate()).isEqualByComparingTo("3.85");
    }

    @Test
    void 주담대_옵션키는_담보_상환_금리유형() throws IOException {
        NormalizedOption o = Normalizer.of(ProductType.MORT)
                .normalizeOption(first("mortgageLoanProductsSearch_020000_p1", "optionList"));

        assertThat(o.optionKey()).isEqualTo("A|D|F");
        assertThat(o.rateMin()).isEqualByComparingTo("5.39");
        assertThat(o.rateAvg()).isEqualByComparingTo("6.03");
    }

    @Test
    void 전세_옵션은_평균금리_null_허용() throws IOException {
        NormalizedOption o = Normalizer.of(ProductType.RENT)
                .normalizeOption(first("rentHouseLoanProductsSearch_020000_p1", "optionList"));

        assertThat(o.optionKey()).isEqualTo("D|C");
        assertThat(o.mortgageType()).isNull();
        assertThat(o.rateAvg()).isNull();
    }

    @Test
    void 필수값이_없으면_MISSING_REQUIRED() throws IOException {
        ObjectNode base = (ObjectNode) first("depositProductsSearch_020000_p1", "baseList");
        base.remove("fin_prdt_nm");

        assertReason(() -> Normalizer.of(ProductType.DEP).normalizeBase(base), NormalizeErrorReason.MISSING_REQUIRED);
    }

    @Test
    void 숫자가_아닌_기간은_TYPE_MISMATCH() throws IOException {
        ObjectNode option = (ObjectNode) first("depositProductsSearch_020000_p1", "optionList");
        option.put("save_trm", "12개월");

        assertReason(() -> Normalizer.of(ProductType.DEP).normalizeOption(option), NormalizeErrorReason.TYPE_MISMATCH);
    }

    @Test
    void 날짜_형식이_틀리면_TYPE_MISMATCH() throws IOException {
        ObjectNode base = (ObjectNode) first("depositProductsSearch_020000_p1", "baseList");
        base.put("dcls_strt_day", "2026-10-20");

        assertReason(() -> Normalizer.of(ProductType.DEP).normalizeBase(base), NormalizeErrorReason.TYPE_MISMATCH);
    }

    @Test
    void 금리가_컬럼_범위를_넘으면_TYPE_MISMATCH() throws IOException {
        ObjectNode option = (ObjectNode) first("mortgageLoanProductsSearch_020000_p1", "optionList");
        option.put("lend_rate_max", 1234.5);

        assertReason(() -> Normalizer.of(ProductType.MORT).normalizeOption(option), NormalizeErrorReason.TYPE_MISMATCH);
    }

    @Test
    void 모르는_코드값은_UNKNOWN_ENUM() throws IOException {
        ObjectNode option = (ObjectNode) first("savingProductsSearch_020000_p1", "optionList");
        option.put("rsrv_type", "X");
        ObjectNode base = (ObjectNode) first("depositProductsSearch_020000_p1", "baseList");
        base.put("join_deny", "9");

        assertReason(() -> Normalizer.of(ProductType.SAV).normalizeOption(option), NormalizeErrorReason.UNKNOWN_ENUM);
        assertReason(() -> Normalizer.of(ProductType.DEP).normalizeBase(base), NormalizeErrorReason.UNKNOWN_ENUM);
    }

    @Test
    void 신용대출은_지원하지_않음() {
        assertThatThrownBy(() -> Normalizer.of(ProductType.CRED)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 실제_원문_전체가_오류_없이_정규화됨() throws IOException {
        try (Stream<Path> files = Files.list(RAW_DIR)) {
            for (Path file : files.filter(NormalizerTest::isProductFile).toList()) {
                Normalizer normalizer = Normalizer.of(typeOf(file));
                JsonNode result = MAPPER.readTree(file.toFile()).path("result");
                result.path("baseList").forEach(normalizer::normalizeBase);
                result.path("optionList").forEach(normalizer::normalizeOption);
            }
        }
    }

    // 신용대출은 미지원, companySearch 는 CompanyNormalizerTest 가 본다
    private static boolean isProductFile(Path file) {
        String name = file.getFileName().toString();
        return !name.startsWith("credit") && !name.startsWith("company");
    }

    private static void assertReason(Runnable call, NormalizeErrorReason reason) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(NormalizeException.class,
                e -> assertThat(e.reason()).isEqualTo(reason));
    }

    private static JsonNode first(String fileStem, String list) throws IOException {
        JsonNode root = MAPPER.readTree(RAW_DIR.resolve(fileStem + "_20261028.json").toFile());
        return root.path("result").path(list).get(0).deepCopy();
    }

    private static ProductType typeOf(Path file) {
        String name = file.getFileName().toString();
        if (name.startsWith("deposit")) return ProductType.DEP;
        if (name.startsWith("saving")) return ProductType.SAV;
        if (name.startsWith("mortgage")) return ProductType.MORT;
        return ProductType.RENT;
    }
}
