package com.gongsi.collect.normalize.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.gongsi.collect.normalize.model.NormalizedOption;
import com.gongsi.collect.normalize.model.ProductKey;
import com.gongsi.common.ProductType;

import java.util.Set;

import static com.gongsi.collect.normalize.parse.JsonFields.code;
import static com.gongsi.collect.normalize.parse.JsonFields.rate;

// 주담대(MORT)·전세(RENT) - 옵션키는 상환|금리유형, 주담대는 앞에 담보유형| 추가
class LoanNormalizer extends ProductBaseNormalizer {

    // A 아파트 / E 아파트외
    private static final Set<String> MORTGAGE_TYPE = Set.of("A", "E");
    // D 분할 / S 만기일시
    private static final Set<String> REPAY_TYPE = Set.of("D", "S");
    // F 고정 / C 변동
    private static final Set<String> LEND_RATE_TYPE = Set.of("F", "C");

    private final boolean isMortgage;

    LoanNormalizer(ProductType type) {
        super(type);
        this.isMortgage = type == ProductType.MORT;
    }

    @Override
    public NormalizedOption normalizeOption(JsonNode o) {
        ProductKey key = key(o);
        String mortgageType = isMortgage ? code(o, "mrtg_type", MORTGAGE_TYPE, true) : null;
        String repayType = code(o, "rpay_type", REPAY_TYPE, true);
        String lendRateType = code(o, "lend_rate_type", LEND_RATE_TYPE, true);
        String optionKey = isMortgage
                ? mortgageType + "|" + repayType + "|" + lendRateType
                : repayType + "|" + lendRateType;
        return new NormalizedOption(key, optionKey, null, null, null, null, null,
                mortgageType, repayType, lendRateType,
                rate(o, "lend_rate_min"), rate(o, "lend_rate_max"), rate(o, "lend_rate_avg"));
    }
}
