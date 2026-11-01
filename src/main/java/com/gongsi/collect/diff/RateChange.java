package com.gongsi.collect.diff;

import com.gongsi.collect.normalize.model.ProductKey;

import java.math.BigDecimal;

// 옵션 금리 필드 하나의 변동 - old 가 null 이면 새로 생김, new 가 null 이면 사라짐
public record RateChange(ProductKey product, String optionKey, RateField field,
                         BigDecimal oldValue, BigDecimal newValue) {
}
