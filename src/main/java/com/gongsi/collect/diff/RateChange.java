package com.gongsi.collect.diff;

import java.math.BigDecimal;

// 옵션 금리 필드 하나의 변동 - old 가 null 이면 새로 생김, new 가 null 이면 사라짐. K 는 상품 자연키
public record RateChange<K>(K product, String optionKey, RateField<?> field,
                            BigDecimal oldValue, BigDecimal newValue) {
}
