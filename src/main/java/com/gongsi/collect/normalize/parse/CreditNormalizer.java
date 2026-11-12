package com.gongsi.collect.normalize.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.gongsi.collect.normalize.model.CreditProductKey;
import com.gongsi.collect.normalize.model.NormalizedCreditOption;
import com.gongsi.collect.normalize.model.NormalizedCreditProduct;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static com.gongsi.collect.normalize.parse.JsonFields.code;
import static com.gongsi.collect.normalize.parse.JsonFields.day;
import static com.gongsi.collect.normalize.parse.JsonFields.minute;
import static com.gongsi.collect.normalize.parse.JsonFields.rate;
import static com.gongsi.collect.normalize.parse.JsonFields.requiredText;
import static com.gongsi.collect.normalize.parse.JsonFields.text;

// 신용대출(CRED) 원문을 credit_loan_product·credit_loan_option 값으로 옮긴다 - 옵션키는 금리 종류
public final class CreditNormalizer
        implements RecordParser<CreditProductKey, NormalizedCreditProduct, NormalizedCreditOption> {

    public static final CreditNormalizer INSTANCE = new CreditNormalizer();

    // 1 일반 / 2 마이너스한도 / 3 카드론
    private static final Set<String> CREDIT_PRODUCT_TYPE = Set.of("1", "2", "3");
    // A 대출금리 / B 기준금리 / C 가산금리 / D 가감조정금리
    private static final Set<String> LEND_RATE_TYPE = Set.of("A", "B", "C", "D");

    private CreditNormalizer() {
    }

    // cb_name 은 원문에 kcb·KCB 가 섞여 대문자로 맞춘다
    @Override
    public NormalizedCreditProduct normalizeBase(JsonNode b) {
        String cbName = text(b, "cb_name", 16);
        return new NormalizedCreditProduct(
                key(b),
                requiredText(b, "kor_co_nm", 100),
                requiredText(b, "fin_prdt_nm", 200),
                text(b, "join_way", 100),
                cbName == null ? null : cbName.toUpperCase(Locale.ROOT),
                requiredText(b, "dcls_month", 6),
                day(b, "dcls_strt_day"),
                day(b, "dcls_end_day"),
                minute(b, "fin_co_subm_day"),
                null);
    }

    @Override
    public NormalizedCreditOption normalizeOption(JsonNode o) {
        return new NormalizedCreditOption(
                key(o),
                code(o, "crdt_lend_rate_type", LEND_RATE_TYPE, true),
                rate(o, "crdt_grad_1"),
                rate(o, "crdt_grad_4"),
                rate(o, "crdt_grad_5"),
                rate(o, "crdt_grad_6"),
                rate(o, "crdt_grad_10"),
                rate(o, "crdt_grad_11"),
                rate(o, "crdt_grad_12"),
                rate(o, "crdt_grad_13"),
                rate(o, "crdt_grad_avg"));
    }

    @Override
    public CreditProductKey keyOf(NormalizedCreditProduct base) {
        return base.key();
    }

    @Override
    public CreditProductKey parentOf(NormalizedCreditOption option) {
        return option.productKey();
    }

    @Override
    public String optionKeyOf(NormalizedCreditOption option) {
        return option.lendRateType();
    }

    @Override
    public NormalizedCreditProduct withFinGrpNo(NormalizedCreditProduct base, String finGrpNo) {
        return base.withFinGrpNo(finGrpNo);
    }

    @Override
    public Optional<CreditProductKey> rawKey(JsonNode record) {
        return RecordParser.rawFields(record, "fin_co_no", "fin_prdt_cd", "crdt_prdt_type")
                .map(v -> new CreditProductKey(v[0], v[1], v[2]));
    }

    // base·option 공통 자연키
    private static CreditProductKey key(JsonNode node) {
        return new CreditProductKey(
                requiredText(node, "fin_co_no", 7),
                requiredText(node, "fin_prdt_cd", 64),
                code(node, "crdt_prdt_type", CREDIT_PRODUCT_TYPE, true));
    }
}
