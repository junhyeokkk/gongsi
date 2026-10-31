package com.gongsi.collect.normalize.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.gongsi.collect.normalize.model.NormalizedProduct;
import com.gongsi.collect.normalize.model.ProductKey;
import com.gongsi.common.ProductType;

import java.util.Set;

import static com.gongsi.collect.normalize.parse.JsonFields.code;
import static com.gongsi.collect.normalize.parse.JsonFields.day;
import static com.gongsi.collect.normalize.parse.JsonFields.longValue;
import static com.gongsi.collect.normalize.parse.JsonFields.minute;
import static com.gongsi.collect.normalize.parse.JsonFields.requiredText;
import static com.gongsi.collect.normalize.parse.JsonFields.text;

// 4개 상품군 공통 baseList 정규화 - 상품군에 없는 필드는 null 로 남는다
abstract class ProductBaseNormalizer implements Normalizer {

    // 1 제한없음 / 2 서민전용 / 3 일부제한
    private static final Set<String> JOIN_DENY = Set.of("1", "2", "3");

    private final ProductType type;

    ProductBaseNormalizer(ProductType type) {
        this.type = type;
    }

    @Override
    public ProductType supports() {
        return type;
    }

    @Override
    public NormalizedProduct normalizeBase(JsonNode b) {
        ProductKey key = key(b);
        String joinDeny = code(b, "join_deny", JOIN_DENY, false);
        return new NormalizedProduct(
                key,
                type,
                requiredText(b, "kor_co_nm", 100),
                requiredText(b, "fin_prdt_nm", 200),
                text(b, "join_way", 100),
                joinDeny == null ? null : Short.valueOf(joinDeny),
                text(b, "join_member"),
                text(b, "etc_note"),
                longValue(b, "max_limit"),
                text(b, "spcl_cnd"),
                text(b, "mtrt_int"),
                text(b, "loan_lmt"),
                text(b, "erly_rpay_fee"),
                text(b, "dly_rate"),
                text(b, "loan_inci_expn"),
                requiredText(b, "dcls_month", 6),
                day(b, "dcls_strt_day"),
                day(b, "dcls_end_day"),
                minute(b, "fin_co_subm_day"),
                null);
    }

    // base·option 공통 자연키
    static ProductKey key(JsonNode node) {
        return new ProductKey(requiredText(node, "fin_co_no", 7), requiredText(node, "fin_prdt_cd", 64));
    }
}
