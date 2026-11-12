package com.gongsi.collect.diff;

import java.util.List;

// 상품군 하나를 직전 원문과 비교한 결과 - 이력·이벤트 테이블에 넣을 행
public record DiffResult<K>(List<RateChange<K>> rateChanges, List<DetectedEvent<K>> events) {
}
