package com.gongsi.collect.normalize.parse;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

// 한 데이터셋의 원문 base·option 레코드를 정규화 값으로 바꾸는 규칙 - K 자연키, B 상품, O 옵션
public interface RecordParser<K, B, O> {

    // baseList 레코드 하나. 오염이면 NormalizeException
    B normalizeBase(JsonNode base);

    // optionList 레코드 하나. 오염이면 NormalizeException
    O normalizeOption(JsonNode option);

    K keyOf(B base);

    // 옵션이 속한 상품의 자연키
    K parentOf(O option);

    // 상품 안에서 옵션을 구분하는 키 - 날마다 같아야 어제 값과 1:1 로 비교된다
    String optionKeyOf(O option);

    // 원문 레코드에 없는 권역을 페이지에서 채운 사본
    B withFinGrpNo(B base, String finGrpNo);

    // 격리된 레코드라도 자연키가 읽히면 어느 상품인지 남긴다
    Optional<K> rawKey(JsonNode record);

    // 원문 필드 값들 - 하나라도 비면 empty
    static Optional<String[]> rawFields(JsonNode record, String... fields) {
        String[] values = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            values[i] = record.path(fields[i]).asText("");
            if (values[i].isBlank()) {
                return Optional.empty();
            }
        }
        return Optional.of(values);
    }
}
