package com.gongsi.collect.diff;

import com.gongsi.collect.normalize.model.ProductKey;
import com.gongsi.common.ProductType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

// 자연키 → product.id / product_option.id - 변동 행을 FK 로 적재하려고 상품군 단위로 한 번 읽는다
@Repository
public class ProductIdRepository {

    private final JdbcTemplate jdbc;

    public ProductIdRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Ids load(ProductType type) {
        Map<ProductKey, Long> products = new HashMap<>();
        Map<OptionRef, Long> options = new HashMap<>();
        jdbc.query("""
                        SELECT p.id AS product_id, p.inst_code, p.product_code, o.id AS option_id, o.option_key
                        FROM product p LEFT JOIN product_option o ON o.product_id = p.id
                        WHERE p.product_type = ?
                        """,
                rs -> {
                    ProductKey key = new ProductKey(rs.getString("inst_code"), rs.getString("product_code"));
                    products.put(key, rs.getLong("product_id"));
                    String optionKey = rs.getString("option_key");
                    if (optionKey != null) {
                        options.put(new OptionRef(key, optionKey), rs.getLong("option_id"));
                    }
                }, type.name());
        return new Ids(products, options);
    }

    // 상품 안에서 옵션을 가리키는 자연키
    public record OptionRef(ProductKey product, String optionKey) {
    }

    // 한 상품군의 자연키 → id 표
    public record Ids(Map<ProductKey, Long> products, Map<OptionRef, Long> options) {

        public Long product(ProductKey key) {
            return products.get(key);
        }

        public Long option(ProductKey key, String optionKey) {
            return options.get(new OptionRef(key, optionKey));
        }
    }
}
