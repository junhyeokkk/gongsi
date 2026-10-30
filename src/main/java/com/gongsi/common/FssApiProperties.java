package com.gongsi.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

// FSS 금융상품 Open API 설정 레코드
@ConfigurationProperties(prefix = "fss")
public record FssApiProperties(
        String baseUrl,
        String authKey,
        int timeoutMs,
        int maxAttempts,     // 첫 호출 포함 총 시도 횟수
        long retryBackoffMs  // 첫 재시도 전 대기 이후 2배씩 늘어남
) {
}
