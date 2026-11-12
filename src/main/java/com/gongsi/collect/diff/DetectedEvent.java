package com.gongsi.collect.diff;

import java.util.List;

// 자연키로 식별한 변동 이벤트 - optionKey 가 null 이면 상품 단위, changes 는 RATE_UP/DOWN 에만 있다
public record DetectedEvent<K>(K product, String optionKey, ChangeEventType type, List<RateChange<K>> changes) {

    static <K> DetectedEvent<K> of(K product, String optionKey, ChangeEventType type) {
        return new DetectedEvent<>(product, optionKey, type, List.of());
    }
}
