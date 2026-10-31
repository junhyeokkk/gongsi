package com.gongsi.collect.normalize.model;

// companySearch baseList 레코드 하나를 company 컬럼과 1:1 로 옮긴 값 - finGrpNo 는 페이지의 권역으로 채운다
public record NormalizedCompany(
        String finCoNo,
        String companyName,
        String homepageUrl,
        String callTel,
        String disclosureContact,
        String disclosureMonth,
        String finGrpNo) {

    // 권역만 바꾼 사본
    public NormalizedCompany withFinGrpNo(String finGrpNo) {
        return new NormalizedCompany(finCoNo, companyName, homepageUrl, callTel, disclosureContact,
                disclosureMonth, finGrpNo);
    }
}
