package com.gongsi.collect.normalize.parse;

// normalize_error.reason 에 들어가는 격리 사유
public enum NormalizeErrorReason {
    MISSING_REQUIRED,  // 필수 필드 없음
    TYPE_MISMATCH,     // 숫자·날짜 형식이 아니거나 컬럼 범위 초과
    UNKNOWN_ENUM,      // 정의되지 않은 코드값
    ORPHAN_OPTION      // 같은 날 원문에 짝이 되는 정상 base 가 없는 옵션
}
