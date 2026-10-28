package com.gongsi.serve.alert;

import org.springframework.stereotype.Service;

/** alert_subscription 테이블 쓰기 전담 */
@Service
public class AlertSubscriptionService {

    // TODO 6주차: JdbcTemplate 주입
    // TODO 6주차: subscribe(request) → id
    //   - INSERT INTO alert_subscription (...) RETURNING id
    //   - 동일 email+조건 중복 체크? → 1차는 중복 허용 (사용자가 여러 조건 등록 가능)
    // TODO 6주차: unsubscribe(id)
    //   - UPDATE alert_subscription SET is_active = false WHERE id = ?
}
