package com.gongsi.collect.normalize.store;

import com.gongsi.collect.normalize.model.NormalizedCompany;
import com.gongsi.collect.normalize.model.NormalizedCompanyArea;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 정규화된 company/company_area 를 DB 에 멱등 적재 (ADR-0002)
@Service
public class CompanyUpsertService {

    // first_seen_on 은 가장 이른 날, last_seen_on 은 가장 늦은 날로 - 재처리 순서와 무관
    private static final String UPSERT_COMPANY_SQL = """
            INSERT INTO company (fin_co_no, fin_grp_no, company_name, homepage_url, call_tel,
                disclosure_contact, disclosure_month, is_active, first_seen_on, last_seen_on)
            VALUES (?, ?, ?, ?, ?, ?, ?, true, ?, ?)
            ON CONFLICT (fin_co_no) DO UPDATE SET
                fin_grp_no = EXCLUDED.fin_grp_no,
                company_name = EXCLUDED.company_name,
                homepage_url = EXCLUDED.homepage_url,
                call_tel = EXCLUDED.call_tel,
                disclosure_contact = EXCLUDED.disclosure_contact,
                disclosure_month = EXCLUDED.disclosure_month,
                is_active = company.is_active OR EXCLUDED.last_seen_on >= company.last_seen_on,
                first_seen_on = LEAST(company.first_seen_on, EXCLUDED.first_seen_on),
                last_seen_on = GREATEST(company.last_seen_on, EXCLUDED.last_seen_on),
                updated_at = now()
            """;

    private static final int[] COMPANY_ARG_TYPES = {
            Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
            Types.VARCHAR, Types.CHAR, Types.DATE, Types.DATE};

    private static final String UPSERT_AREA_SQL = """
            INSERT INTO company_area (company_id, area_code, area_name, has_branch, last_seen_on)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (company_id, area_code) DO UPDATE SET
                area_name = EXCLUDED.area_name,
                has_branch = EXCLUDED.has_branch,
                last_seen_on = GREATEST(company_area.last_seen_on, EXCLUDED.last_seen_on),
                updated_at = now()
            """;

    private static final int[] AREA_ARG_TYPES = {Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.BOOLEAN, Types.DATE};

    private final JdbcTemplate jdbc;

    public CompanyUpsertService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 회사를 적재하고 fin_co_no → company.id 를 돌려줌
    public Map<String, Long> upsertCompanies(List<NormalizedCompany> companies, LocalDate seenOn) {
        Date seen = Date.valueOf(seenOn);
        List<Object[]> args = companies.stream().map(c -> new Object[]{
                c.finCoNo(), c.finGrpNo(), c.companyName(), c.homepageUrl(), c.callTel(),
                c.disclosureContact(), c.disclosureMonth(), seen, seen}).toList();
        jdbc.batchUpdate(UPSERT_COMPANY_SQL, args, COMPANY_ARG_TYPES);
        return companyIds();
    }

    // 시·도 영업점 유무 적재 - companyIds 는 upsertCompanies 결과
    public void upsertAreas(List<NormalizedCompanyArea> areas, Map<String, Long> companyIds, LocalDate seenOn) {
        Date seen = Date.valueOf(seenOn);
        List<Object[]> args = areas.stream().map(a -> new Object[]{
                companyIds.get(a.finCoNo()), a.areaCode(), a.areaName(), a.hasBranch(), seen}).toList();
        jdbc.batchUpdate(UPSERT_AREA_SQL, args, AREA_ARG_TYPES);
    }

    // seenOn 원문에 없던 회사를 비활성으로 - 비활성이 된 회사 수 반환
    public int deactivateMissing(LocalDate seenOn) {
        return jdbc.update("UPDATE company SET is_active = false, updated_at = now() WHERE is_active AND last_seen_on < ?",
                seenOn);
    }

    private Map<String, Long> companyIds() {
        Map<String, Long> ids = new HashMap<>();
        jdbc.query("SELECT id, fin_co_no FROM company", rs -> {
            ids.put(rs.getString("fin_co_no"), rs.getLong("id"));
        });
        return ids;
    }
}
