package com.gongsi.collect.normalize.model;

// 상품군 안에서 상품을 식별하는 자연키 (fin_co_no, fin_prdt_cd)
public record ProductKey(String instCode, String productCode) {
}
