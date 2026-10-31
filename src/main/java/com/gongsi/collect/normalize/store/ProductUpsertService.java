package com.gongsi.collect.normalize.store;

import com.gongsi.collect.normalize.model.NormalizedOption;
import com.gongsi.collect.normalize.model.NormalizedProduct;
import com.gongsi.collect.normalize.model.ProductKey;
import com.gongsi.common.ProductType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 정규화된 product/product_option 을 DB 에 멱등 적재 (ADR-0002)
@Service
public class ProductUpsertService {

    // first_seen_on 은 가장 이른 날, last_seen_on 은 가장 늦은 날로 - 재처리 순서와 무관
    private static final String UPSERT_PRODUCT_SQL = """
            INSERT INTO product (inst_code, product_code, product_type, inst_name, product_name, join_way,
                join_deny, join_member, etc_note, max_limit, special_condition, maturity_interest_note,
                loan_limit_note, early_repayment_fee_note, delinquency_rate_note, incidental_expense_note,
                disclosure_month, disclosure_start_date, disclosure_end_date, submitted_at, fin_grp_no,
                is_active, first_seen_on, last_seen_on)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, ?, ?)
            ON CONFLICT (inst_code, product_code, product_type) DO UPDATE SET
                inst_name = EXCLUDED.inst_name,
                product_name = EXCLUDED.product_name,
                join_way = EXCLUDED.join_way,
                join_deny = EXCLUDED.join_deny,
                join_member = EXCLUDED.join_member,
                etc_note = EXCLUDED.etc_note,
                max_limit = EXCLUDED.max_limit,
                special_condition = EXCLUDED.special_condition,
                maturity_interest_note = EXCLUDED.maturity_interest_note,
                loan_limit_note = EXCLUDED.loan_limit_note,
                early_repayment_fee_note = EXCLUDED.early_repayment_fee_note,
                delinquency_rate_note = EXCLUDED.delinquency_rate_note,
                incidental_expense_note = EXCLUDED.incidental_expense_note,
                disclosure_month = EXCLUDED.disclosure_month,
                disclosure_start_date = EXCLUDED.disclosure_start_date,
                disclosure_end_date = EXCLUDED.disclosure_end_date,
                submitted_at = EXCLUDED.submitted_at,
                fin_grp_no = EXCLUDED.fin_grp_no,
                is_active = product.is_active OR EXCLUDED.last_seen_on >= product.last_seen_on,
                first_seen_on = LEAST(product.first_seen_on, EXCLUDED.first_seen_on),
                last_seen_on = GREATEST(product.last_seen_on, EXCLUDED.last_seen_on),
                updated_at = now()
            """;

    private static final int[] PRODUCT_ARG_TYPES = {
            Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
            Types.SMALLINT, Types.VARCHAR, Types.VARCHAR, Types.BIGINT, Types.VARCHAR, Types.VARCHAR,
            Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
            Types.CHAR, Types.DATE, Types.DATE, Types.TIMESTAMP, Types.VARCHAR,
            Types.DATE, Types.DATE};

    private static final String UPSERT_OPTION_SQL = """
            INSERT INTO product_option (product_id, option_key, save_term, rate_type, reserve_type,
                base_rate, max_rate, mortgage_type, repay_type, lend_rate_type, rate_min, rate_max, rate_avg,
                is_active, last_seen_on)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, ?)
            ON CONFLICT (product_id, option_key) DO UPDATE SET
                base_rate = EXCLUDED.base_rate,
                max_rate = EXCLUDED.max_rate,
                rate_min = EXCLUDED.rate_min,
                rate_max = EXCLUDED.rate_max,
                rate_avg = EXCLUDED.rate_avg,
                is_active = product_option.is_active OR EXCLUDED.last_seen_on >= product_option.last_seen_on,
                last_seen_on = GREATEST(product_option.last_seen_on, EXCLUDED.last_seen_on),
                updated_at = now()
            """;

    private static final int[] OPTION_ARG_TYPES = {
            Types.BIGINT, Types.VARCHAR, Types.SMALLINT, Types.CHAR, Types.CHAR,
            Types.NUMERIC, Types.NUMERIC, Types.CHAR, Types.CHAR, Types.CHAR,
            Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.DATE};

    private final JdbcTemplate jdbc;

    public ProductUpsertService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 상품을 적재하고 그 상품군의 자연키 → product.id 를 돌려줌
    public Map<ProductKey, Long> upsertProducts(ProductType type, List<NormalizedProduct> products, LocalDate seenOn) {
        Date seen = Date.valueOf(seenOn);
        List<Object[]> args = products.stream().map(p -> new Object[]{
                p.key().instCode(), p.key().productCode(), type.name(), p.instName(), p.productName(),
                p.joinWay(), p.joinDeny(), p.joinMember(), p.etcNote(), p.maxLimit(), p.specialCondition(),
                p.maturityInterestNote(), p.loanLimitNote(), p.earlyRepaymentFeeNote(),
                p.delinquencyRateNote(), p.incidentalExpenseNote(), p.disclosureMonth(),
                toDate(p.disclosureStartDate()), toDate(p.disclosureEndDate()),
                p.submittedAt() == null ? null : Timestamp.valueOf(p.submittedAt()), p.finGrpNo(),
                seen, seen}).toList();
        jdbc.batchUpdate(UPSERT_PRODUCT_SQL, args, PRODUCT_ARG_TYPES);
        return productIds(type);
    }

    // 옵션 적재 - productIds 는 upsertProducts 결과
    public void upsertOptions(List<NormalizedOption> options, Map<ProductKey, Long> productIds, LocalDate seenOn) {
        Date seen = Date.valueOf(seenOn);
        List<Object[]> args = options.stream().map(o -> new Object[]{
                productIds.get(o.productKey()), o.optionKey(), o.saveTerm(), o.rateType(), o.reserveType(),
                o.baseRate(), o.maxRate(), o.mortgageType(), o.repayType(), o.lendRateType(),
                o.rateMin(), o.rateMax(), o.rateAvg(), seen}).toList();
        jdbc.batchUpdate(UPSERT_OPTION_SQL, args, OPTION_ARG_TYPES);
    }

    // seenOn 원문에 없던 상품·옵션을 비활성으로 - 비활성이 된 상품 수 반환
    public int deactivateMissing(ProductType type, LocalDate seenOn) {
        jdbc.update("""
                UPDATE product_option o SET is_active = false, updated_at = now()
                FROM product p
                WHERE p.id = o.product_id AND p.product_type = ? AND o.is_active AND o.last_seen_on < ?
                """, type.name(), seenOn);
        return jdbc.update("""
                UPDATE product SET is_active = false, updated_at = now()
                WHERE product_type = ? AND is_active AND last_seen_on < ?
                """, type.name(), seenOn);
    }

    private Map<ProductKey, Long> productIds(ProductType type) {
        Map<ProductKey, Long> ids = new HashMap<>();
        jdbc.query("SELECT id, inst_code, product_code FROM product WHERE product_type = ?",
                rs -> {
                    ids.put(new ProductKey(rs.getString("inst_code"), rs.getString("product_code")), rs.getLong("id"));
                }, type.name());
        return ids;
    }

    private static Date toDate(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }
}
