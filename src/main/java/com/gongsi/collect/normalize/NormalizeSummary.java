package com.gongsi.collect.normalize;

import com.gongsi.common.ProductType;

// 상품군 하나의 정규화 결과 - 적재한 상품·옵션 수, 격리한 레코드 수, 비활성으로 바뀐 상품 수
public record NormalizeSummary(ProductType type, int pages, int products, int options, int errors, int deactivated) {
}
