package com.gongsi.common;

/** FSS 상품군 코드. DB 의 product_type VARCHAR(8) 과 정확히 일치해야 함 */
public enum ProductType {
    DEP,   // 정기예금
    SAV,   // 적금
    MORT,  // 주택담보대출
    RENT,  // 전세자금대출
    CRED   // 신용대출
}
