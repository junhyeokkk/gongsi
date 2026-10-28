package com.gongsi.serve.product;

import org.springframework.stereotype.Service;

/** 상품 조회 쿼리 전담. 쓰기 X — JdbcTemplate 또는 JPA readOnly */
@Service
public class ProductQueryService {

    // TODO 5주차: JdbcTemplate 주입
    // TODO 5주차: findByFilter(type, saveTerm, minRate, instCode, pageable) → Page<ProductSummary>
    //   - JOIN product_option ON product_id = product.id
    //   - WHERE is_active = true AND product_type = ? AND option 조건
    //   - 성능: idx_product_type_active, idx_product_option_product_term 활용
    // TODO 5주차: findDetail(productId) → ProductDetail
    //   - product + 모든 활성 option + 각 option 의 최근 rate_history 30 일
    // TODO 5주차: 신용대출 — findCreditByFilter / findCreditDetail 분리
    //   테이블 구조가 다르므로 (credit_loan_product + credit_loan_option)
}
