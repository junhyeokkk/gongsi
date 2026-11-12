package com.gongsi.collect.normalize.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.gongsi.collect.normalize.model.NormalizedOption;
import com.gongsi.collect.normalize.model.NormalizedProduct;
import com.gongsi.collect.normalize.model.ProductKey;
import com.gongsi.common.ProductType;

import java.util.Optional;

// 상품군별 정규화 전략 - 원문 레코드 하나를 product / product_option 한 행으로 바꿈
public interface Normalizer extends RecordParser<ProductKey, NormalizedProduct, NormalizedOption> {

    ProductType supports();

    @Override
    default ProductKey keyOf(NormalizedProduct base) {
        return base.key();
    }

    @Override
    default ProductKey parentOf(NormalizedOption option) {
        return option.productKey();
    }

    @Override
    default String optionKeyOf(NormalizedOption option) {
        return option.optionKey();
    }

    @Override
    default NormalizedProduct withFinGrpNo(NormalizedProduct base, String finGrpNo) {
        return base.withFinGrpNo(finGrpNo);
    }

    @Override
    default Optional<ProductKey> rawKey(JsonNode record) {
        return RecordParser.rawFields(record, "fin_co_no", "fin_prdt_cd").map(v -> new ProductKey(v[0], v[1]));
    }

    // CRED 는 credit_loan_* 로 따로 적재하므로 CreditNormalizer 가 맡는다
    static Normalizer of(ProductType type) {
        return switch (type) {
            case DEP, SAV -> new DepositNormalizer(type);
            case MORT, RENT -> new LoanNormalizer(type);
            case CRED -> throw new IllegalArgumentException("신용대출은 product 정규화 대상이 아님");
        };
    }
}
