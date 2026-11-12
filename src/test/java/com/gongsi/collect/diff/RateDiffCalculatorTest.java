package com.gongsi.collect.diff;

import com.gongsi.collect.normalize.model.CreditProductKey;
import com.gongsi.collect.normalize.model.NormalizedCreditOption;
import com.gongsi.collect.normalize.model.NormalizedOption;
import com.gongsi.collect.normalize.model.ProductKey;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

// 직전·오늘 상품→옵션 표를 비교해 이력과 이벤트를 뽑는 규칙 검증
class RateDiffCalculatorTest {

    private static final ProductKey A = new ProductKey("0010001", "A");
    private static final ProductKey B = new ProductKey("0010001", "B");

    @Test
    void 오른_필드와_내린_필드는_각각_이벤트_하나() {
        var prev = Map.of(A, options(deposit(A, "12|S", "2.50", "3.00")));
        var today = Map.of(A, options(deposit(A, "12|S", "2.40", "3.20")));

        DiffResult<ProductKey> result = RateDiffCalculator.compare(prev, today, Set.of(), ProductRateField.ALL);

        assertThat(result.rateChanges()).extracting(RateChange::field, RateChange::oldValue, RateChange::newValue)
                .containsExactly(tuple(ProductRateField.BASE_RATE, dec("2.50"), dec("2.40")),
                        tuple(ProductRateField.MAX_RATE, dec("3.00"), dec("3.20")));
        assertThat(result.events()).extracting(DetectedEvent::type, e -> e.changes().size())
                .containsExactly(tuple(ChangeEventType.RATE_UP, 1), tuple(ChangeEventType.RATE_DOWN, 1));
    }

    @Test
    void 자릿수만_다른_금리는_변동이_아님() {
        var prev = Map.of(A, options(deposit(A, "12|S", "2.5", "3")));
        var today = Map.of(A, options(deposit(A, "12|S", "2.50", "3.00")));

        DiffResult<ProductKey> result = RateDiffCalculator.compare(prev, today, Set.of(), ProductRateField.ALL);

        assertThat(result.rateChanges()).isEmpty();
        assertThat(result.events()).isEmpty();
    }

    @Test
    void null_이_낀_변화는_이력만_남기고_이벤트는_없음() {
        var prev = Map.of(A, options(deposit(A, "12|S", "2.50", null)));
        var today = Map.of(A, options(deposit(A, "12|S", "2.50", "3.00")));

        DiffResult<ProductKey> result = RateDiffCalculator.compare(prev, today, Set.of(), ProductRateField.ALL);

        assertThat(result.rateChanges()).extracting(RateChange::field, RateChange::oldValue)
                .containsExactly(tuple(ProductRateField.MAX_RATE, null));
        assertThat(result.events()).isEmpty();
    }

    @Test
    void 새_옵션과_사라진_옵션() {
        var prev = Map.of(A, options(deposit(A, "12|S", "2.50", "3.00"), deposit(A, "6|S", "2.00", "2.20")));
        var today = Map.of(A, options(deposit(A, "12|S", "2.50", "3.00"), deposit(A, "24|S", "2.70", "3.10")));

        DiffResult<ProductKey> result = RateDiffCalculator.compare(prev, today, Set.of(), ProductRateField.ALL);

        assertThat(result.events()).extracting(DetectedEvent::optionKey, DetectedEvent::type)
                .containsExactly(tuple("24|S", ChangeEventType.NEW), tuple("6|S", ChangeEventType.OPTION_REMOVED));
        assertThat(result.rateChanges()).extracting(RateChange::optionKey, RateChange::oldValue, RateChange::newValue)
                .containsExactly(
                        tuple("24|S", null, dec("2.70")), tuple("24|S", null, dec("3.10")),
                        tuple("6|S", dec("2.00"), null), tuple("6|S", dec("2.20"), null));
    }

    @Test
    void 새_상품과_사라진_상품은_상품_단위_이벤트() {
        var prev = Map.of(A, options(deposit(A, "12|S", "2.50", "3.00")));
        var today = Map.of(B, options(deposit(B, "12|S", "2.60", null)));

        DiffResult<ProductKey> result = RateDiffCalculator.compare(prev, today, Set.of(), ProductRateField.ALL);

        assertThat(result.events()).extracting(DetectedEvent::product, DetectedEvent::optionKey, DetectedEvent::type)
                .containsExactly(tuple(B, null, ChangeEventType.NEW), tuple(A, null, ChangeEventType.CLOSED));
        assertThat(result.rateChanges()).extracting(RateChange::product, RateChange::field)
                .containsExactly(tuple(B, ProductRateField.BASE_RATE),
                        tuple(A, ProductRateField.BASE_RATE), tuple(A, ProductRateField.MAX_RATE));
    }

    @Test
    void 오늘_격리된_상품은_사라짐으로_판정하지_않음() {
        var prev = Map.of(A, options(deposit(A, "12|S", "2.50", "3.00"), deposit(A, "6|S", "2.00", "2.20")),
                B, options(deposit(B, "12|S", "2.50", "3.00")));
        var today = Map.of(A, options(deposit(A, "12|S", "2.50", "3.00")));

        DiffResult<ProductKey> result = RateDiffCalculator.compare(prev, today, Set.of(A, B), ProductRateField.ALL);

        assertThat(result.events()).isEmpty();
        assertThat(result.rateChanges()).isEmpty();
    }

    @Test
    void 대출은_최저_최고_금리를_비교하고_평균은_무시() {
        var prev = Map.of(A, options(loan(A, "A|D|F", "3.10", "5.00", "4.00")));
        var today = Map.of(A, options(loan(A, "A|D|F", "3.10", "4.80", "4.50")));

        DiffResult<ProductKey> result = RateDiffCalculator.compare(prev, today, Set.of(), ProductRateField.ALL);

        assertThat(result.rateChanges()).extracting(RateChange::field).containsExactly(ProductRateField.RATE_MAX);
        assertThat(result.events()).extracting(DetectedEvent::type).containsExactly(ChangeEventType.RATE_DOWN);
    }

    @Test
    void 신용대출은_구간금리와_평균을_비교하고_금리종류가_옵션키() {
        CreditProductKey c = new CreditProductKey("0010016", "WR0002F", "2");
        var prev = Map.of(c, Map.of("A", credit(c, "A", "6.68", null, "6.77"), "D", credit(c, "D", "0.47", null, "0.39")));
        var today = Map.of(c, Map.of("A", credit(c, "A", "6.90", "7.10", "6.70")));

        DiffResult<CreditProductKey> result = RateDiffCalculator.compare(prev, today, Set.of(), CreditRateField.ALL);

        assertThat(result.events()).extracting(DetectedEvent::optionKey, DetectedEvent::type)
                .containsExactlyInAnyOrder(tuple("A", ChangeEventType.RATE_UP), tuple("A", ChangeEventType.RATE_DOWN),
                        tuple("D", ChangeEventType.OPTION_REMOVED));
        assertThat(result.rateChanges()).filteredOn(ch -> ch.optionKey().equals("A"))
                .extracting(RateChange::field, RateChange::oldValue)
                .containsExactly(tuple(CreditRateField.RATE_GRADE_1, dec("6.68")),
                        tuple(CreditRateField.RATE_GRADE_10, null), tuple(CreditRateField.RATE_AVG, dec("6.77")));
    }

    private static NormalizedCreditOption credit(CreditProductKey key, String type, String g1, String g10, String avg) {
        return new NormalizedCreditOption(key, type, dec(g1), null, null, null, dec(g10), null, null, null, dec(avg));
    }

    private static Map<String, NormalizedOption> options(NormalizedOption... options) {
        Map<String, NormalizedOption> m = new LinkedHashMap<>();
        List.of(options).forEach(o -> m.put(o.optionKey(), o));
        return m;
    }

    private static NormalizedOption deposit(ProductKey key, String optionKey, String base, String max) {
        return new NormalizedOption(key, optionKey, (short) 12, "S", null, dec(base), dec(max),
                null, null, null, null, null, null);
    }

    private static NormalizedOption loan(ProductKey key, String optionKey, String min, String max, String avg) {
        return new NormalizedOption(key, optionKey, null, null, null, null, null,
                "A", "D", "F", dec(min), dec(max), dec(avg));
    }

    private static BigDecimal dec(String v) {
        return v == null ? null : new BigDecimal(v);
    }
}
