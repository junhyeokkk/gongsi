package com.gongsi.collect.diff;

import com.fasterxml.jackson.databind.JsonNode;
import com.gongsi.collect.fetch.RawSnapshotRepository.RawPage;
import com.gongsi.collect.normalize.RawRecordReader;
import com.gongsi.collect.normalize.RawRecordReader.Records;
import com.gongsi.collect.normalize.RawRecordReader.Rejects;
import com.gongsi.collect.normalize.parse.NormalizeException;
import com.gongsi.collect.normalize.parse.RecordParser;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 원문 페이지를 normalize 와 같은 규칙으로 다시 읽어 상품 → 옵션키 → 옵션으로 모은다. 격리 레코드는 그 상품 자연키만 남긴다
@Component
public class RawRateReader {

    private final RawRecordReader reader;

    public RawRateReader(RawRecordReader reader) {
        this.reader = reader;
    }

    public <K, B, O> RawRates<K, O> read(RecordParser<K, B, O> parser, List<RawPage> pages) {
        Set<K> quarantined = new HashSet<>();
        Records<K, B, O> records = reader.read(pages, parser, new Rejects<>() {
            @Override
            public void failed(RawPage page, String listKind, int index, JsonNode record, NormalizeException e) {
                parser.rawKey(record).ifPresent(quarantined::add);
            }

            // base 가 없는 옵션은 옵션 테이블에도 없다
            @Override
            public void orphan(RawPage page, int index, K parent) {
                quarantined.add(parent);
            }
        });
        Map<K, Map<String, O>> products = new LinkedHashMap<>();
        records.bases().keySet().forEach(k -> products.put(k, new LinkedHashMap<>()));
        records.options().forEach(o -> products.get(parser.parentOf(o)).put(parser.optionKeyOf(o), o));
        return new RawRates<>(products, quarantined);
    }
}
