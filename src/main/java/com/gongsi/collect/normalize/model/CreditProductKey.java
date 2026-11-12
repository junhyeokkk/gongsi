package com.gongsi.collect.normalize.model;

// 신용대출 상품 자연키 (fin_co_no, fin_prdt_cd, crdt_prdt_type) - 같은 상품코드가 일반·마이너스한도·카드론으로 나뉜다
public record CreditProductKey(String instCode, String productCode, String creditProductType) {
}
