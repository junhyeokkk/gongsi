package com.gongsi.collect.normalize.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.gongsi.collect.normalize.model.NormalizedCompany;
import com.gongsi.collect.normalize.model.NormalizedCompanyArea;

import java.util.Set;

import static com.gongsi.collect.normalize.parse.JsonFields.code;
import static com.gongsi.collect.normalize.parse.JsonFields.requiredText;
import static com.gongsi.collect.normalize.parse.JsonFields.text;

// companySearch 원문 레코드를 company·company_area 값으로 옮긴다 - 오염 레코드는 NormalizeException
public final class CompanyNormalizer {

    private static final Set<String> YN = Set.of("Y", "N");

    private CompanyNormalizer() {
    }

    public static NormalizedCompany normalizeBase(JsonNode b) {
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
    public static NormalizedCompanyArea normalizeOption(JsonNode o) {
        return new NormalizedCompanyArea(
                finCoNo(o),
                requiredText(o, "area_cd", 2),
                requiredText(o, "area_nm", 16),
                "Y".equals(code(o, "exis_yn", YN, true)));
    }

    private static String finCoNo(JsonNode node) {
        return requiredText(node, "fin_co_no", 7);
    }
}
