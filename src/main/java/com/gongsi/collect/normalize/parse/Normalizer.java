package com.gongsi.collect.normalize.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.gongsi.collect.normalize.model.NormalizedOption;
import com.gongsi.collect.normalize.model.NormalizedProduct;
import com.gongsi.common.ProductType;

// 상품군별 정규화 전략 - 원문 레코드 하나를 product / product_option 한 행으로 바꿈
public interface Normalizer {

    ProductType supports();

    // baseList 레코드 하나. 오염이면 NormalizeException
    NormalizedProduct normalizeBase(JsonNode base);

    // optionList 레코드 하나. 오염이면 NormalizeException
    NormalizedOption normalizeOption(JsonNode option);

    // CRED 는 credit_loan_* 로 따로 적재하므로 여기서 다루지 않는다
    static Normalizer of(ProductType type) {
        return switch (type) {
            case DEP, SAV -> new DepositNormalizer(type);
            case MORT, RENT -> new LoanNormalizer(type);
            case CRED -> throw new IllegalArgumentException("신용대출은 product 정규화 대상이 아님");
        };
    }
}
