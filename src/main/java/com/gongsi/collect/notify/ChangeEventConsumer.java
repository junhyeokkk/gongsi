package com.gongsi.collect.notify;

import org.springframework.stereotype.Component;

/** change_event 중 consumed_at IS NULL 인 이벤트를 폴링. idx_change_event_pending 부분 인덱스 활용 */
@Component
public class ChangeEventConsumer {

    // TODO 6주차: JdbcTemplate 주입 + AlertDispatcher 주입
    // TODO 6주차: consumePending(batchSize)
    //   - SELECT ... FROM change_event WHERE consumed_at IS NULL ORDER BY id LIMIT ?
    //   - 각 이벤트에 대해 매칭되는 alert_subscription 조회
    //   - AlertDispatcher.dispatch(subscription, event) 호출
    //   - 성공 시 UPDATE change_event SET consumed_at = now() WHERE id = ?
    //   - 실패해도 다음 이벤트 계속 (alert_delivery 에 FAILED 로 기록)

    public int consumePending(int batchSize) {
        throw new UnsupportedOperationException("6주차 구현 예정");
    }
}
