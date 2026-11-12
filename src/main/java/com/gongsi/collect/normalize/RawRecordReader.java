package com.gongsi.collect.normalize;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongsi.collect.fetch.RawSnapshotRepository.RawPage;
import com.gongsi.collect.normalize.parse.NormalizeException;
import com.gongsi.collect.normalize.parse.RecordParser;
import com.gongsi.common.GongsiException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 하루치 원문 페이지를 정규화 값으로 모은다 - 오염 레코드와 고아 옵션은 rejects 로 넘기고 계속
@Component
public class RawRecordReader {

    private final ObjectMapper objectMapper;

    public RawRecordReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // 고아 판정은 전 페이지의 정상 base 를 모은 뒤에 한다 - base 와 옵션이 다른 페이지에 있을 수 있다
    public <K, B, O> Records<K, B, O> read(List<RawPage> pages, RecordParser<K, B, O> parser, Rejects<K> rejects) {
        Map<K, B> bases = new LinkedHashMap<>();
        List<Pending<O>> pending = new ArrayList<>();
        for (RawPage page : pages) {
            JsonNode result = parse(page).path("result");
            JsonNode baseList = result.path("baseList");
            for (int i = 0; i < baseList.size(); i++) {
                try {
                    B base = parser.withFinGrpNo(parser.normalizeBase(baseList.get(i)), page.finGrpNo());
                    bases.put(parser.keyOf(base), base);
                } catch (NormalizeException e) {
                    rejects.failed(page, "BASE", i, baseList.get(i), e);
                }
            }
            JsonNode optionList = result.path("optionList");
            for (int i = 0; i < optionList.size(); i++) {
                try {
                    pending.add(new Pending<>(page, i, parser.normalizeOption(optionList.get(i))));
                } catch (NormalizeException e) {
                    rejects.failed(page, "OPTION", i, optionList.get(i), e);
                }
            }
        }
        List<O> options = new ArrayList<>();
        for (Pending<O> p : pending) {
            K parent = parser.parentOf(p.option());
            if (bases.containsKey(parent)) {
                options.add(p.option());
            } else {
                rejects.orphan(p.page(), p.index(), parent);
            }
        }
        return new Records<>(bases, options);
    }

    private JsonNode parse(RawPage page) {
        try {
            return objectMapper.readTree(page.payload());
        } catch (JsonProcessingException e) {
            // fetch 가 파싱을 통과한 원문만 적재하므로 여기 오면 DB 가 오염된 것
            throw new GongsiException("raw_snapshot 파싱 실패 id=" + page.id(), e);
        }
    }

    // 정상 레코드 - 자연키 → 상품(같은 키는 뒤 페이지가 이긴다), 부모가 있는 옵션
    public record Records<K, B, O>(Map<K, B> bases, List<O> options) {
    }

    // 걸러낸 레코드를 받는 쪽 - normalize 는 normalize_error 에 남기고 diff 는 상품 자연키만 모은다
    public interface Rejects<K> {

        // listKind: BASE / OPTION, index: 페이지 안 배열 위치
        void failed(RawPage page, String listKind, int index, JsonNode record, NormalizeException e);

        // 같은 날 원문에 정상 base 가 없는 옵션
        void orphan(RawPage page, int index, K parent);
    }

    // 고아 판정 전까지 원문 위치를 들고 있는 옵션
    private record Pending<O>(RawPage page, int index, O option) {
    }
}
