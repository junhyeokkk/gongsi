package com.gongsi.collect.normalize.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 신용대출 baseList 레코드 하나를 credit_loan_product 컬럼과 1:1 로 옮긴 값 - finGrpNo 는 페이지의 권역으로 채운다
public record NormalizedCreditProduct(
        CreditProductKey key,
        String instName,
        String productName,
        String joinWay,
        String cbName,
        String disclosureMonth,
        LocalDate disclosureStartDate,
        LocalDate disclosureEndDate,
        LocalDateTime submittedAt,
        String finGrpNo) {

    // 권역만 바꾼 사본
    public NormalizedCreditProduct withFinGrpNo(String finGrpNo) {
        return new NormalizedCreditProduct(key, instName, productName, joinWay, cbName, disclosureMonth,
                disclosureStartDate, disclosureEndDate, submittedAt, finGrpNo);
    }
}
