package com.gongsi.collect.diff;

import com.gongsi.collect.normalize.model.NormalizedOption;
import com.gongsi.collect.normalize.model.ProductKey;

import java.util.Map;
import java.util.Set;

// 하루 원문을 다시 정규화한 결과 - 상품 → 옵션키 → 옵션, 그리고 격리 레코드가 있던 상품 자연키
public record RawRates(Map<ProductKey, Map<String, NormalizedOption>> products, Set<ProductKey> quarantined) {
}
