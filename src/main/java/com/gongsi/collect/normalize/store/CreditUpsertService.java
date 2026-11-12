package com.gongsi.collect.normalize.store;

import com.gongsi.collect.normalize.model.CreditProductKey;
import com.gongsi.collect.normalize.model.NormalizedCreditOption;
import com.gongsi.collect.normalize.model.NormalizedCreditProduct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 정규화된 credit_loan_product/credit_loan_option 을 DB 에 멱등 적재 (ADR-0002)
@Service
public class CreditUpsertService
        implements ProductStore<CreditProductKey, NormalizedCreditProduct, NormalizedCreditOption> {

    // first_seen_on 은 가장 이른 날, last_seen_on 은 가장 늦은 날로 - 재처리 순서와 무관
    private static final String UPSERT_PRODUCT_SQL = """
            INSERT INTO credit_loan_product (inst_code, product_code, credit_product_type, inst_name, product_name,
                join_way, cb_name, disclosure_month, disclosure_start_date, disclosure_end_date, submitted_at,
                fin_grp_no, is_active, first_seen_on, last_seen_on)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, ?, ?)
            ON CONFLICT (inst_code, product_code, credit_product_type) DO UPDATE SET
                inst_name = EXCLUDED.inst_name,
                product_name = EXCLUDED.product_name,
                join_way = EXCLUDED.join_way,
                cb_name = EXCLUDED.cb_name,
                disclosure_month = EXCLUDED.disclosure_month,
                disclosure_start_date = EXCLUDED.disclosure_start_date,
                disclosure_end_date = EXCLUDED.disclosure_end_date,
                submitted_at = EXCLUDED.submitted_at,
                fin_grp_no = EXCLUDED.fin_grp_no,
                is_active = credit_loan_product.is_active OR EXCLUDED.last_seen_on >= credit_loan_product.last_seen_on,
                first_seen_on = LEAST(credit_loan_product.first_seen_on, EXCLUDED.first_seen_on),
                last_seen_on = GREATEST(credit_loan_product.last_seen_on, EXCLUDED.last_seen_on),
                updated_at = now()
            """;

    private static final int[] PRODUCT_ARG_TYPES = {
            Types.VARCHAR, Types.VARCHAR, Types.CHAR, Types.VARCHAR, Types.VARCHAR,
            Types.VARCHAR, Types.VARCHAR, Types.CHAR, Types.DATE, Types.DATE, Types.TIMESTAMP,
            Types.VARCHAR, Types.DATE, Types.DATE};

    private static final String UPSERT_OPTION_SQL = """
            INSERT INTO credit_loan_option (credit_loan_product_id, lend_rate_type, rate_grade_1, rate_grade_4,
                rate_grade_5, rate_grade_6, rate_grade_10, rate_grade_11, rate_grade_12, rate_grade_13, rate_avg,
                is_active, last_seen_on)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, ?)
            ON CONFLICT (credit_loan_product_id, lend_rate_type) DO UPDATE SET
                rate_grade_1 = EXCLUDED.rate_grade_1,
                rate_grade_4 = EXCLUDED.rate_grade_4,
                rate_grade_5 = EXCLUDED.rate_grade_5,
                rate_grade_6 = EXCLUDED.rate_grade_6,
                rate_grade_10 = EXCLUDED.rate_grade_10,
                rate_grade_11 = EXCLUDED.rate_grade_11,
                rate_grade_12 = EXCLUDED.rate_grade_12,
                rate_grade_13 = EXCLUDED.rate_grade_13,
                rate_avg = EXCLUDED.rate_avg,
                is_active = credit_loan_option.is_active OR EXCLUDED.last_seen_on >= credit_loan_option.last_seen_on,
                last_seen_on = GREATEST(credit_loan_option.last_seen_on, EXCLUDED.last_seen_on),
                updated_at = now()
            """;

    private static final int[] OPTION_ARG_TYPES = {
            Types.BIGINT, Types.CHAR, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
            Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.DATE};

    private final JdbcTemplate jdbc;

    public CreditUpsertService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<CreditProductKey, Long> upsertProducts(List<NormalizedCreditProduct> products, LocalDate seenOn) {
        Date seen = Date.valueOf(seenOn);
        List<Object[]> args = products.stream().map(p -> new Object[]{
                p.key().instCode(), p.key().productCode(), p.key().creditProductType(), p.instName(),
                p.productName(), p.joinWay(), p.cbName(), p.disclosureMonth(),
                toDate(p.disclosureStartDate()), toDate(p.disclosureEndDate()),
                p.submittedAt() == null ? null : Timestamp.valueOf(p.submittedAt()), p.finGrpNo(),
                seen, seen}).toList();
        jdbc.batchUpdate(UPSERT_PRODUCT_SQL, args, PRODUCT_ARG_TYPES);
        return productIds();
    }

    @Override
    public void upsertOptions(List<NormalizedCreditOption> options, Map<CreditProductKey, Long> productIds,
                              LocalDate seenOn) {
        Date seen = Date.valueOf(seenOn);
        List<Object[]> args = options.stream().map(o -> new Object[]{
                productIds.get(o.productKey()), o.lendRateType(), o.rateGrade1(), o.rateGrade4(), o.rateGrade5(),
                o.rateGrade6(), o.rateGrade10(), o.rateGrade11(), o.rateGrade12(), o.rateGrade13(), o.rateAvg(),
                seen}).toList();
        jdbc.batchUpdate(UPSERT_OPTION_SQL, args, OPTION_ARG_TYPES);
    }

    @Override
    public int deactivateMissing(LocalDate seenOn) {
        jdbc.update("""
                UPDATE credit_loan_option SET is_active = false, updated_at = now()
                WHERE is_active AND last_seen_on < ?
                """, seenOn);
        return jdbc.update("""
                UPDATE credit_loan_product SET is_active = false, updated_at = now()
                WHERE is_active AND last_seen_on < ?
                """, seenOn);
    }

    private Map<CreditProductKey, Long> productIds() {
        Map<CreditProductKey, Long> ids = new HashMap<>();
        jdbc.query("SELECT id, inst_code, product_code, credit_product_type FROM credit_loan_product", rs -> {
            ids.put(new CreditProductKey(rs.getString("inst_code"), rs.getString("product_code"),
                    rs.getString("credit_product_type")), rs.getLong("id"));
        });
        return ids;
    }

    private static Date toDate(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }
}
