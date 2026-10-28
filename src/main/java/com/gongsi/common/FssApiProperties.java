package com.gongsi.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** FSS 금융상품 Open API 설정. application.yml 의 fss.* 바인딩 */
// TODO 2주차: application.yml 에 fss.base-url, fss.auth-key, fss.timeout-ms 추가
@ConfigurationProperties(prefix = "fss")
public record FssApiProperties(
        String baseUrl,
        String authKey,
        int timeoutMs
) {
}
