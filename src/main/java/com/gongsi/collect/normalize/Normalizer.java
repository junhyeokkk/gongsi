package com.gongsi.collect.normalize;

import com.gongsi.common.ProductType;

/** 상품군별 정규화 전략. ProductType 당 구현체 1개 */
// TODO 3주차: 구현체 5종 분기
//   - DepositNormalizer (DEP)
//   - SavingNormalizer (SAV)
//   - MortgageLoanNormalizer (MORT)
//   - RentLoanNormalizer (RENT)
//   - CreditLoanNormalizer (CRED) — product 가 아니라 credit_loan_product 로 적재
// TODO 3주차: NormalizerRegistry 로 ProductType → Normalizer 조회
public interface Normalizer {

    ProductType supports();

    // TODO 3주차: normalize(rawPayload) 반환 타입 설계
    //   - baseList / optionList 를 담은 record 하나로? 아니면 두 메서드로 분리?
    //   - 각 record 는 upsert 하기 좋은 평면 구조 (DB 컬럼과 1:1)
}
