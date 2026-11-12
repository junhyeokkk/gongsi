package com.gongsi.collect.normalize.store;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// 한 상품군의 정규화 값을 상품·옵션 테이블에 멱등 적재 (ADR-0002)
public interface ProductStore<K, B, O> {

    // 상품을 적재하고 그 상품군의 자연키 → 상품 id 를 돌려줌
    Map<K, Long> upsertProducts(List<B> products, LocalDate seenOn);

    // 옵션 적재 - productIds 는 upsertProducts 결과
    void upsertOptions(List<O> options, Map<K, Long> productIds, LocalDate seenOn);

    // seenOn 원문에 없던 상품·옵션을 비활성으로 - 비활성이 된 상품 수 반환
    int deactivateMissing(LocalDate seenOn);
}
