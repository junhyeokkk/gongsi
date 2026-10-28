package com.gongsi.collect.normalize;

import com.gongsi.common.ProductType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** 정규화 실패 레코드를 normalize_error 에 기록. 배치는 계속 진행 (한 레코드가 전체를 깨뜨리면 안됨) */
@Component
public class NormalizeErrorRecorder {

    // TODO 3주차: JdbcTemplate 주입
    // TODO 3주차: record(fetchedOn, type, rawSnapshotId, listKind, recordIndex, reason, detail)
    //   - reason: MISSING_REQUIRED / TYPE_MISMATCH / UNKNOWN_ENUM / ORPHAN_OPTION
    //   - detail 에 원본 JSON 조각 요약 (전체 아님 — PII/용량)

    public void record(LocalDate fetchedOn, ProductType type, long rawSnapshotId,
                       String listKind, int recordIndex, String reason, String detail) {
        throw new UnsupportedOperationException("3주차 구현 예정");
    }
}
