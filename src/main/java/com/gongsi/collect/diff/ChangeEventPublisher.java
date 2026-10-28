package com.gongsi.collect.diff;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** change_event insert. uk_change_event_* 부분 UNIQUE 인덱스로 멱등 보장 */
@Component
public class ChangeEventPublisher {

    // TODO 4주차: JdbcTemplate 주입
    // TODO 4주차: publishProductEvent(productId, optionId or null, eventType, occurredOn, payloadJson)
    //   - product_id XOR credit_loan_product_id 체크 제약 주의
    // TODO 4주차: publishCreditEvent(creditLoanProductId, creditLoanOptionId or null, eventType, occurredOn, payloadJson)
    // TODO 4주차: eventType enum 또는 상수:
    //   NEW / RATE_UP / RATE_DOWN / CLOSED / OPTION_REMOVED

    public void publishProductEvent(long productId, Long optionId, String eventType,
                                    LocalDate occurredOn, String payloadJson) {
        throw new UnsupportedOperationException("4주차 구현 예정");
    }
}
