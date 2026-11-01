package com.gongsi.collect.diff;

import com.gongsi.collect.normalize.model.NormalizedOption;
import com.gongsi.collect.normalize.model.ProductKey;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// 직전·오늘 원문의 상품→옵션키→옵션을 비교해 금리 변동과 이벤트를 뽑는다
public final class RateDiffCalculator {

    private RateDiffCalculator() {
    }

    public static DiffResult compare(Map<ProductKey, Map<String, NormalizedOption>> prev,
                                     Map<ProductKey, Map<String, NormalizedOption>> today) {
        return compare(prev, today, Set.of());
    }

    // quarantined 상품은 오늘 레코드가 격리돼 안 보일 수 있어 사라짐(CLOSED·OPTION_REMOVED)으로 판정하지 않는다
    public static DiffResult compare(Map<ProductKey, Map<String, NormalizedOption>> prev,
                                     Map<ProductKey, Map<String, NormalizedOption>> today,
                                     Set<ProductKey> quarantined) {
        List<RateChange> changes = new ArrayList<>();
        List<DetectedEvent> events = new ArrayList<>();
        today.forEach((product, todayOptions) -> {
            Map<String, NormalizedOption> prevOptions = prev.get(product);
            if (prevOptions == null) {
                events.add(DetectedEvent.of(product, null, ChangeEventType.NEW));
                todayOptions.values().forEach(o -> changes.addAll(appeared(product, o)));
                return;
            }
            todayOptions.forEach((key, option) -> {
                NormalizedOption before = prevOptions.get(key);
                if (before == null) {
                    events.add(DetectedEvent.of(product, key, ChangeEventType.NEW));
                    changes.addAll(appeared(product, option));
                } else {
                    compareOption(product, key, before, option, changes, events);
                }
            });
            prevOptions.forEach((key, option) -> {
                if (!todayOptions.containsKey(key) && !quarantined.contains(product)) {
                    events.add(DetectedEvent.of(product, key, ChangeEventType.OPTION_REMOVED));
                    changes.addAll(disappeared(product, option));
                }
            });
        });
        prev.forEach((product, prevOptions) -> {
            if (!today.containsKey(product) && !quarantined.contains(product)) {
                events.add(DetectedEvent.of(product, null, ChangeEventType.CLOSED));
                prevOptions.values().forEach(o -> changes.addAll(disappeared(product, o)));
            }
        });
        return new DiffResult(changes, events);
    }

    // 필드마다 이력을 남기고, 오른 필드·내린 필드를 각각 이벤트 하나로 묶는다. null 이 낀 변화는 이력만
    private static void compareOption(ProductKey product, String key, NormalizedOption before, NormalizedOption after,
                                      List<RateChange> changes, List<DetectedEvent> events) {
        List<RateChange> ups = new ArrayList<>();
        List<RateChange> downs = new ArrayList<>();
        for (RateField field : RateField.values()) {
            BigDecimal old = field.of(before);
            BigDecimal now = field.of(after);
            if (sameRate(old, now)) {
                continue;
            }
            RateChange change = new RateChange(product, key, field, old, now);
            changes.add(change);
            if (old != null && now != null) {
                (now.compareTo(old) > 0 ? ups : downs).add(change);
            }
        }
        if (!ups.isEmpty()) {
            events.add(new DetectedEvent(product, key, ChangeEventType.RATE_UP, ups));
        }
        if (!downs.isEmpty()) {
            events.add(new DetectedEvent(product, key, ChangeEventType.RATE_DOWN, downs));
        }
    }

    // 2.5 와 2.50 은 같은 금리
    private static boolean sameRate(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return Objects.equals(a, b);
        }
        return a.compareTo(b) == 0;
    }

    private static List<RateChange> appeared(ProductKey product, NormalizedOption option) {
        List<RateChange> rows = new ArrayList<>();
        for (RateField field : RateField.values()) {
            if (field.of(option) != null) {
                rows.add(new RateChange(product, option.optionKey(), field, null, field.of(option)));
            }
        }
        return rows;
    }

    private static List<RateChange> disappeared(ProductKey product, NormalizedOption option) {
        List<RateChange> rows = new ArrayList<>();
        for (RateField field : RateField.values()) {
            if (field.of(option) != null) {
                rows.add(new RateChange(product, option.optionKey(), field, field.of(option), null));
            }
        }
        return rows;
    }
}
