package com.gongsi.collect.normalize.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gongsi.collect.normalize.model.NormalizedCompany;
import com.gongsi.collect.normalize.model.NormalizedCompanyArea;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// companySearch 원문 레코드가 company·company_area 값으로 옮겨지고 오염 레코드는 사유별로 걸러지는지 검증
class CompanyNormalizerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path RAW_DIR = Path.of("survey/raw");

    @Test
    void 회사_base_를_컬럼으로_옮김() throws IOException {
        NormalizedCompany c = CompanyNormalizer.normalizeBase(first("baseList"));

        assertThat(c.finCoNo()).isEqualTo("0010001");
        assertThat(c.companyName()).isEqualTo("우리은행");
        assertThat(c.homepageUrl()).startsWith("https://");
        assertThat(c.callTel()).isEqualTo("15885000");
        assertThat(c.disclosureContact()).contains("1588-5000");
        assertThat(c.disclosureMonth()).isEqualTo("202610");
        assertThat(c.finGrpNo()).isNull();
    }

    @Test
    void 지역_옵션은_exis_yn_을_영업점_유무로() throws IOException {
        NormalizedCompanyArea a = CompanyNormalizer.normalizeOption(first("optionList"));

        assertThat(a).isEqualTo(new NormalizedCompanyArea("0010001", "01", "서울", true));
    }

    @Test
    void 회사명이_없으면_MISSING_REQUIRED() throws IOException {
        ObjectNode b = (ObjectNode) first("baseList");
        b.remove("kor_co_nm");

        assertThatThrownBy(() -> CompanyNormalizer.normalizeBase(b))
                .isInstanceOfSatisfying(NormalizeException.class,
                        e -> assertThat(e.reason()).isEqualTo(NormalizeErrorReason.MISSING_REQUIRED));
    }

    @Test
    void exis_yn_이_Y_N_밖이면_UNKNOWN_ENUM() throws IOException {
        ObjectNode o = (ObjectNode) first("optionList");
        o.put("exis_yn", "X");

        assertThatThrownBy(() -> CompanyNormalizer.normalizeOption(o))
                .isInstanceOfSatisfying(NormalizeException.class,
                        e -> assertThat(e.reason()).isEqualTo(NormalizeErrorReason.UNKNOWN_ENUM));
    }

    @Test
    void 콜센터_번호가_컬럼보다_길면_TYPE_MISMATCH() throws IOException {
        ObjectNode b = (ObjectNode) first("baseList");
        b.put("cal_tel", "1".repeat(21));

        assertThatThrownBy(() -> CompanyNormalizer.normalizeBase(b))
                .isInstanceOfSatisfying(NormalizeException.class,
                        e -> assertThat(e.reason()).isEqualTo(NormalizeErrorReason.TYPE_MISMATCH));
    }

    @Test
    void 실제_원문_전체가_오류_없이_정규화됨() throws IOException {
        for (String finGrpNo : new String[]{"020000", "030300"}) {
            JsonNode result = MAPPER.readTree(RAW_DIR.resolve("companySearch_" + finGrpNo + "_p1_20261031.json").toFile())
                    .path("result");
            result.path("baseList").forEach(CompanyNormalizer::normalizeBase);
            result.path("optionList").forEach(CompanyNormalizer::normalizeOption);
        }
    }

    private static JsonNode first(String list) throws IOException {
        JsonNode root = MAPPER.readTree(RAW_DIR.resolve("companySearch_020000_p1_20261031.json").toFile());
        return root.path("result").path(list).get(0).deepCopy();
    }
}
