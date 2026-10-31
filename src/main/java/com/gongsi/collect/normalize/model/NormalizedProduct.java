package com.gongsi.collect.normalize.model;

import com.gongsi.common.ProductType;

import java.time.LocalDate;
import java.time.LocalDateTime;

// baseList 레코드 하나를 product 컬럼과 1:1 로 옮긴 값 - finGrpNo 는 원문에 없어 페이지의 권역으로 채운다
public record NormalizedProduct(
        ProductKey key,
        ProductType type,
        String instName,
        String productName,
        String joinWay,
        Short joinDeny,
        String joinMember,
        String etcNote,
        Long maxLimit,
        String specialCondition,
        String maturityInterestNote,
        String loanLimitNote,
        String earlyRepaymentFeeNote,
        String delinquencyRateNote,
        String incidentalExpenseNote,
        String disclosureMonth,
        LocalDate disclosureStartDate,
        LocalDate disclosureEndDate,
        LocalDateTime submittedAt,
        String finGrpNo) {

    // 권역만 바꾼 사본
    public NormalizedProduct withFinGrpNo(String finGrpNo) {
        return new NormalizedProduct(key, type, instName, productName, joinWay, joinDeny, joinMember, etcNote,
                maxLimit, specialCondition, maturityInterestNote, loanLimitNote, earlyRepaymentFeeNote,
                delinquencyRateNote, incidentalExpenseNote, disclosureMonth, disclosureStartDate,
                disclosureEndDate, submittedAt, finGrpNo);
    }
}
