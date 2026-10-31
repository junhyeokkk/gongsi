package com.gongsi.collect.normalize.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.gongsi.collect.normalize.model.NormalizedOption;
import com.gongsi.collect.normalize.model.ProductKey;
import com.gongsi.common.ProductType;

import java.util.Set;

import static com.gongsi.collect.normalize.parse.JsonFields.code;
import static com.gongsi.collect.normalize.parse.JsonFields.rate;
import static com.gongsi.collect.normalize.parse.JsonFields.requiredShort;

// 예금(DEP)·적금(SAV) - 옵션키는 기간|금리유형, 적금은 |적립유형 추가
class DepositNormalizer extends ProductBaseNormalizer {

    // S 단리 / M 복리
    private static final Set<String> RATE_TYPE = Set.of("S", "M");
    // S 정액 / F 자유
    private static final Set<String> RESERVE_TYPE = Set.of("S", "F");

    private final boolean isSaving;

    DepositNormalizer(ProductType type) {
        super(type);
        this.isSaving = type == ProductType.SAV;
    }

    @Override
    public NormalizedOption normalizeOption(JsonNode o) {
        ProductKey key = key(o);
        Short saveTerm = requiredShort(o, "save_trm");
        String rateType = code(o, "intr_rate_type", RATE_TYPE, true);
        String reserveType = isSaving ? code(o, "rsrv_type", RESERVE_TYPE, true) : null;
        String optionKey = isSaving
                ? saveTerm + "|" + rateType + "|" + reserveType
                : saveTerm + "|" + rateType;
        return new NormalizedOption(key, optionKey, saveTerm, rateType, reserveType,
                rate(o, "intr_rate"), rate(o, "intr_rate2"),
                null, null, null, null, null, null);
    }
}
