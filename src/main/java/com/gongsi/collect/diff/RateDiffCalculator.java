package com.gongsi.collect.diff;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// 직전·오늘 원문의 상품→옵션키→옵션을 비교해 금리 변동과 이벤트를 뽑는다 - K 상품 자연키, O 옵션
public final class RateDiffCalculator {

    private RateDiffCalculator() {
    }

    // quarantined 상품은 오늘 레코드가 격리돼 안 보일 수 있어 사라짐(CLOSED·OPTION_REMOVED)으로 판정하지 않는다
    public static <K, O> DiffResult<K> compare(Map<K, Map<String, O>> prev, Map<K, Map<String, O>> today,
                                               Set<K> quarantined, List<? extends RateField<O>> fields) {
        List<RateChange<K>> changes = new ArrayList<>();
        List<DetectedEvent<K>> events = new ArrayList<>();
        today.forEach((product, todayOptions) -> {
            Map<String, O> prevOptions = prev.get(product);
            if (prevOptions == null) {
                events.add(DetectedEvent.of(product, null, ChangeEventType.NEW));
                todayOptions.forEach((key, o) -> changes.addAll(appeared(product, key, o, fields)));
                return;
            }
            todayOptions.forEach((key, option) -> {
                O before = prevOptions.get(key);
                if (before == null) {
                    events.add(DetectedEvent.of(product, key, ChangeEventType.NEW));
                    changes.addAll(appeared(product, key, option, fields));
                } else {
                    compareOption(product, key, before, option, fields, changes, events);
                }
            });
            prevOptions.forEach((key, option) -> {
                if (!todayOptions.containsKey(key) && !quarantined.contains(product)) {
                    events.add(DetectedEvent.of(product, key, ChangeEventType.OPTION_REMOVED));
                    changes.addAll(disappeared(product, key, option, fields));
                }
            });
        });
        prev.forEach((product, prevOptions) -> {
            if (!today.containsKey(product) && !quarantined.contains(product)) {
                events.add(DetectedEvent.of(product, null, ChangeEventType.CLOSED));
                prevOptions.forEach((key, o) -> changes.addAll(disappeared(product, key, o, fields)));
            }
        });
        return new DiffResult<>(changes, events);
    }

    // 필드마다 이력을 남기고, 오른 필드·내린 필드를 각각 이벤트 하나로 묶는다. null 이 낀 변화는 이력만
    private static <K, O> void compareOption(K product, String key, O before, O after,
                                             List<? extends RateField<O>> fields,
                                             List<RateChange<K>> changes, List<DetectedEvent<K>> events) {
        List<RateChange<K>> ups = new ArrayList<>();
        List<RateChange<K>> downs = new ArrayList<>();
        for (RateField<O> field : fields) {
            BigDecimal old = field.of(before);
            BigDecimal now = field.of(after);
            if (sameRate(old, now)) {
                continue;
            }
            RateChange<K> change = new RateChange<>(product, key, field, old, now);
            changes.add(change);
            if (old != null && now != null) {
                (now.compareTo(old) > 0 ? ups : downs).add(change);
            }
        }
        if (!ups.isEmpty()) {
            events.add(new DetectedEvent<>(product, key, ChangeEventType.RATE_UP, ups));
        }
        if (!downs.isEmpty()) {
            events.add(new DetectedEvent<>(product, key, ChangeEventType.RATE_DOWN, downs));
        }
    }

    // 2.5 와 2.50 은 같은 금리
    private static boolean sameRate(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return Objects.equals(a, b);
        }
        return a.compareTo(b) == 0;
    }

    private static <K, O> List<RateChange<K>> appeared(K product, String key, O option,
                                                       List<? extends RateField<O>> fields) {
        List<RateChange<K>> rows = new ArrayList<>();
        for (RateField<O> field : fields) {
            if (field.of(option) != null) {
                rows.add(new RateChange<>(product, key, field, null, field.of(option)));
            }
        }
        return rows;
    }

    private static <K, O> List<RateChange<K>> disappeared(K product, String key, O option,
                                                          List<? extends RateField<O>> fields) {
        List<RateChange<K>> rows = new ArrayList<>();
        for (RateField<O> field : fields) {
            if (field.of(option) != null) {
                rows.add(new RateChange<>(product, key, field, field.of(option), null));
            }
        }
        return rows;
    }
}
