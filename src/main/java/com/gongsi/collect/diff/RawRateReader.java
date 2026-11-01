package com.gongsi.collect.diff;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongsi.collect.fetch.RawSnapshotRepository.RawPage;
import com.gongsi.collect.normalize.model.NormalizedOption;
import com.gongsi.collect.normalize.model.ProductKey;
import com.gongsi.collect.normalize.parse.NormalizeException;
import com.gongsi.collect.normalize.parse.Normalizer;
import com.gongsi.common.GongsiException;
import com.gongsi.common.ProductType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// 원문 페이지를 다시 정규화해 상품 → 옵션키 → 옵션으로 모은다. 격리 대상 레코드는 버리고 그 상품 자연키만 남긴다
@Component
public class RawRateReader {

    private final ObjectMapper objectMapper;

    public RawRateReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RawRates read(ProductType type, List<RawPage> pages) {
        Normalizer normalizer = Normalizer.of(type);
        Map<ProductKey, Map<String, NormalizedOption>> products = new LinkedHashMap<>();
        Map<ProductKey, Map<String, NormalizedOption>> options = new HashMap<>();
        Set<ProductKey> quarantined = new HashSet<>();
        for (RawPage page : pages) {
            JsonNode result = parse(page).path("result");
            for (JsonNode base : result.path("baseList")) {
                try {
                    products.putIfAbsent(normalizer.normalizeBase(base).key(), new LinkedHashMap<>());
                } catch (NormalizeException e) {
                    rawKey(base).ifPresent(quarantined::add);
                }
            }
            for (JsonNode raw : result.path("optionList")) {
                try {
                    NormalizedOption o = normalizer.normalizeOption(raw);
                    options.computeIfAbsent(o.productKey(), k -> new LinkedHashMap<>()).put(o.optionKey(), o);
                } catch (NormalizeException e) {
                    rawKey(raw).ifPresent(quarantined::add);
                }
            }
        }
        // base 가 없는 옵션(ORPHAN_OPTION)은 product_option 에도 없다
        options.keySet().stream().filter(k -> !products.containsKey(k)).forEach(quarantined::add);
        products.forEach((key, opts) -> opts.putAll(options.getOrDefault(key, Map.of())));
        return new RawRates(products, quarantined);
    }

    // 격리된 레코드라도 자연키가 읽히면 어느 상품인지 남긴다
    private static Optional<ProductKey> rawKey(JsonNode record) {
        String inst = record.path("fin_co_no").asText("");
        String code = record.path("fin_prdt_cd").asText("");
        return inst.isBlank() || code.isBlank() ? Optional.empty() : Optional.of(new ProductKey(inst, code));
    }

    private JsonNode parse(RawPage page) {
        try {
            return objectMapper.readTree(page.payload());
        } catch (JsonProcessingException e) {
            throw new GongsiException("raw_snapshot 파싱 실패 id=" + page.id(), e);
        }
    }
}
