package com.gongsi.collect.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** 슬랙 Incoming Webhook 알림 (URL 이 비어 있으면 로그만) */
@Component
public class SlackCollectAlert implements CollectAlert {

    private static final Logger log = LoggerFactory.getLogger(SlackCollectAlert.class);
    private static final int TIMEOUT_MS = 5000; // 알림이 Job 을 붙잡지 않게

    private final RestClient restClient;
    private final String webhookUrl;

    public SlackCollectAlert(RestClient.Builder builder, @Value("${alert.slack.webhook-url:}") String webhookUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        this.restClient = builder.requestFactory(factory).build();
        this.webhookUrl = webhookUrl;
    }

    // 발송 실패는 warn 만 남긴다 [알림 때문에 수집이 멈추면 안 됨]
    @Override
    public void send(String text) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.info("슬랙 웹훅 미설정 - 알림 생략: {}", text);
            return;
        }
        try {
            restClient.post().uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("슬랙 알림 발송 실패: {} - {}", e.getClass().getSimpleName(), text);
        }
    }
}
