package com.gongsi.collect.diff;

import com.gongsi.common.ProductType;

import java.util.Map;

// 상품군 하나의 변동 감지 결과 - 감지한 이벤트 종류별 수, 새로 적재한 행 수, id 를 못 찾아 버린 행 수
public record DiffSummary(ProductType type, Map<ChangeEventType, Long> detected,
                          int rateRowsInserted, int eventsInserted, int unresolved) {
}
