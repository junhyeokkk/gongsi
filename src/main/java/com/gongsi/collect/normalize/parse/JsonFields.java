package com.gongsi.collect.normalize.parse;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;

import static com.gongsi.collect.normalize.parse.NormalizeErrorReason.MISSING_REQUIRED;
import static com.gongsi.collect.normalize.parse.NormalizeErrorReason.TYPE_MISMATCH;
import static com.gongsi.collect.normalize.parse.NormalizeErrorReason.UNKNOWN_ENUM;

// 원문 레코드에서 필드를 꺼내 타입을 맞춤 - 실패하면 사유를 담은 NormalizeException
final class JsonFields {

    // NUMERIC(5,2) 에 들어가는 최대 절댓값
    private static final BigDecimal RATE_LIMIT = new BigDecimal("999.99");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("uuuuMMdd");
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("uuuuMMddHHmm");
    // 종료일 없음을 뜻하는 값
    private static final String OPEN_END_DAY = "99991231";

    private JsonFields() {
    }

    // 없거나 빈 문자열이면 null
    static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    // 컬럼 길이를 넘으면 TYPE_MISMATCH
    static String text(JsonNode node, String field, int maxLength) {
        String s = text(node, field);
        if (s != null && s.length() > maxLength) {
            throw new NormalizeException(TYPE_MISMATCH, field + " 길이 " + s.length() + " > " + maxLength);
        }
        return s;
    }

    static String requiredText(JsonNode node, String field, int maxLength) {
        String s = text(node, field, maxLength);
        if (s == null) {
            throw new NormalizeException(MISSING_REQUIRED, field + " 없음");
        }
        return s;
    }

    // allowed 밖의 값이면 UNKNOWN_ENUM
    static String code(JsonNode node, String field, Set<String> allowed, boolean required) {
        String s = required ? requiredText(node, field, Integer.MAX_VALUE) : text(node, field);
        if (s != null && !allowed.contains(s)) {
            throw new NormalizeException(UNKNOWN_ENUM, field + "=" + s);
        }
        return s;
    }

    static Short requiredShort(JsonNode node, String field) {
        String s = requiredText(node, field, Integer.MAX_VALUE);
        try {
            return Short.valueOf(s);
        } catch (NumberFormatException e) {
            throw new NormalizeException(TYPE_MISMATCH, field + "=" + s);
        }
    }

    static Long longValue(JsonNode node, String field) {
        String s = text(node, field);
        if (s == null) {
            return null;
        }
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            throw new NormalizeException(TYPE_MISMATCH, field + "=" + s);
        }
    }

    static BigDecimal rate(JsonNode node, String field) {
        String s = text(node, field);
        if (s == null) {
            return null;
        }
        BigDecimal v;
        try {
            v = new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new NormalizeException(TYPE_MISMATCH, field + "=" + s);
        }
        if (v.abs().compareTo(RATE_LIMIT) > 0) {
            throw new NormalizeException(TYPE_MISMATCH, field + "=" + s + " 범위 초과");
        }
        return v;
    }

    // yyyyMMdd. 99991231 은 null
    static LocalDate day(JsonNode node, String field) {
        String s = text(node, field);
        if (s == null || OPEN_END_DAY.equals(s)) {
            return null;
        }
        try {
            return LocalDate.parse(s, DAY);
        } catch (DateTimeParseException e) {
            throw new NormalizeException(TYPE_MISMATCH, field + "=" + s);
        }
    }

    // yyyyMMddHHmm
    static LocalDateTime minute(JsonNode node, String field) {
        String s = text(node, field);
        if (s == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(s, MINUTE);
        } catch (DateTimeParseException e) {
            throw new NormalizeException(TYPE_MISMATCH, field + "=" + s);
        }
    }
}
