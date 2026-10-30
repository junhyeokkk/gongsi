package com.gongsi.collect.fetch;

import com.gongsi.common.FssApiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** FSS 전용 RestClient. baseUrl 과 connect/read 타임아웃을 properties 로 맞춤 */
@Configuration
@EnableConfigurationProperties(FssApiProperties.class)
public class FssClientConfig {

    @Bean
    public RestClient fssRestClient(RestClient.Builder builder, FssApiProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.timeoutMs());
        factory.setReadTimeout(props.timeoutMs());
        return builder.baseUrl(props.baseUrl()).requestFactory(factory).build();
    }
}
