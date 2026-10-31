package com.gongsi.collect.normalize.store;

import com.gongsi.collect.normalize.parse.NormalizeErrorReason;
import com.gongsi.common.ProductType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// 정규화 실패 레코드를 normalize_error 에 기록. 배치는 계속 진행 (한 레코드가 전체를 깨뜨리면 안됨)
@Component
public class NormalizeErrorRecorder {

    // 원문 전체가 아니라 키·사유만 남긴다 (용량)
    private static final int DETAIL_MAX = 500;

    private final JdbcTemplate jdbc;

    public NormalizeErrorRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 같은 날 재실행 시 이전 기록을 지워 에러가 중복되지 않게 함
    public void clear(LocalDate fetchedOn, ProductType type) {
        jdbc.update("DELETE FROM normalize_error WHERE fetched_on = ? AND product_type = ?",
                fetchedOn, type.name());
    }

    // listKind: BASE / OPTION, recordIndex: 페이지 안 배열 위치
    public void record(LocalDate fetchedOn, ProductType type, long rawSnapshotId,
                       String listKind, int recordIndex, NormalizeErrorReason reason, String detail) {
        String trimmed = detail != null && detail.length() > DETAIL_MAX ? detail.substring(0, DETAIL_MAX) : detail;
        jdbc.update("""
                INSERT INTO normalize_error
                    (fetched_on, product_type, raw_snapshot_id, list_kind, record_index, reason, detail)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, fetchedOn, type.name(), rawSnapshotId, listKind, recordIndex, reason.name(), trimmed);
    }
}
