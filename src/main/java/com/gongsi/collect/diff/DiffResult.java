package com.gongsi.collect.diff;

import java.util.List;

// 상품군 하나를 직전 원문과 비교한 결과 - rate_history·change_event 에 넣을 행
public record DiffResult(List<RateChange> rateChanges, List<DetectedEvent> events) {
}
