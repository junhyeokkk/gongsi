package com.gongsi.collect.normalize.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.gongsi.collect.normalize.model.NormalizedCompany;
import com.gongsi.collect.normalize.model.NormalizedCompanyArea;

import java.util.Optional;
import java.util.Set;

import static com.gongsi.collect.normalize.parse.JsonFields.code;
import static com.gongsi.collect.normalize.parse.JsonFields.requiredText;
import static com.gongsi.collect.normalize.parse.JsonFields.text;

// companySearch 원문 레코드를 company·company_area 값으로 옮긴다 - 자연키는 fin_co_no, 옵션키는 시·도 코드
public final class CompanyNormalizer implements RecordParser<String, NormalizedCompany, NormalizedCompanyArea> {

    public static final CompanyNormalizer INSTANCE = new CompanyNormalizer();

    private static final Set<String> YN = Set.of("Y", "N");

    private CompanyNormalizer() {
    }

    @Override
    public NormalizedCompany normalizeBase(JsonNode b) {
        return new NormalizedCompany(
                finCoNo(b),
                requiredText(b, "kor_co_nm", 100),
                text(b, "homp_url", 300),
                text(b, "cal_tel", 20),
                text(b, "dcls_chrg_man"),
                requiredText(b, "dcls_month", 6),
                null);
    }

    // 시·도 코드는 새 값이 생겨도 격리하지 않도록 길이만 본다
    @Override
    public NormalizedCompanyArea normalizeOption(JsonNode o) {
        return new NormalizedCompanyArea(
                finCoNo(o),
                requiredText(o, "area_cd", 2),
                requiredText(o, "area_nm", 16),
                "Y".equals(code(o, "exis_yn", YN, true)));
    }

    @Override
    public String keyOf(NormalizedCompany base) {
        return base.finCoNo();
    }

    @Override
    public String parentOf(NormalizedCompanyArea option) {
        return option.finCoNo();
    }

    @Override
    public String optionKeyOf(NormalizedCompanyArea option) {
        return option.areaCode();
    }

    @Override
    public NormalizedCompany withFinGrpNo(NormalizedCompany base, String finGrpNo) {
        return base.withFinGrpNo(finGrpNo);
    }

    @Override
    public Optional<String> rawKey(JsonNode record) {
        return RecordParser.rawFields(record, "fin_co_no").map(v -> v[0]);
    }

    private static String finCoNo(JsonNode node) {
        return requiredText(node, "fin_co_no", 7);
    }
}
