package com.gongsi.collect.normalize;

import org.springframework.stereotype.Service;

/** 정규화된 product/product_option 을 DB 에 멱등 적재. uk_product 로 UPSERT */
@Service
public class ProductUpsertService {

    // TODO 3주차: JdbcTemplate 주입
    // TODO 3주차: upsertProduct(normalizedProduct) → productId 반환
    //   - ON CONFLICT (inst_code, product_code, product_type) DO UPDATE
    //     상품명/공시월/last_seen_on/is_active 업데이트, first_seen_on 은 그대로
    // TODO 3주차: upsertOption(productId, normalizedOption) → optionId 반환
    //   - option_key 조립 규칙 확정 필요 (DEP/SAV: save_term|rate_type|reserve_type)
    //   - ON CONFLICT (product_id, option_key) DO UPDATE 요율/활성 상태 업데이트
    // TODO 3주차: deactivateMissing(fetchedOn, productType)
    //   - last_seen_on < fetchedOn 인 product/product_option 을 is_active=false 로
    //   - 유사하게 credit_loan_* 테이블용 서비스 분리 (CreditLoanUpsertService) 필요
}
