package com.gongsi.serve.alert;

import com.gongsi.common.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사용자 알림 구독 등록·해제 API */
@RestController
@RequestMapping("/api/alerts")
public class AlertSubscriptionController {

    private final AlertSubscriptionService service;

    public AlertSubscriptionController(AlertSubscriptionService service) {
        this.service = service;
    }

    // TODO 6주차: POST /api/alerts/subscribe
    //   요청 body: email, productType?, saveTerm?, minRate?, instCode?, eventTypes (CSV)
    //   응답: subscriptionId
    //   유효성: email 포맷, eventTypes 는 NEW/RATE_UP/... 중 하나 이상
    @PostMapping("/subscribe")
    public ApiResponse<Long> subscribe(@RequestBody Object request) {
        throw new UnsupportedOperationException("6주차 구현 예정");
    }

    // TODO 6주차: DELETE /api/alerts/{id} — 구독 해제 (is_active=false 로 soft delete)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> unsubscribe(@PathVariable Long id) {
        throw new UnsupportedOperationException("6주차 구현 예정");
    }
}
