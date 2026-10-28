package com.gongsi.collect.notify;

import org.springframework.stereotype.Component;

/** 알림 전송 추상화. 1차는 이메일, 나중에 webhook/push 추가 가능 */
@Component
public class AlertDispatcher {

    // TODO 6주차: 전송 채널 추상화 — AlertChannel 인터페이스 분리 고려
    //   현재는 EmailAlertChannel 하나만 — 과설계 피하고 JavaMailSender 로 직접 보내도 됨
    // TODO 6주차: dispatch(subscriptionId, changeEventId, email, subject, body)
    //   - 전송 시도 → alert_delivery 에 PENDING 로 insert (멱등: uk_alert_delivery)
    //   - 전송 성공 → status=SENT, sent_at=now()
    //   - 실패 → status=FAILED, attempt_count++, last_error=메시지
    // TODO 6주차: 재시도 정책 — attempt_count < 3 까지 다음 폴링 때 재시도
}
