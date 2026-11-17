package com.gongsi.collect.job;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;

/** 웹훅이 없거나 닿지 않아도 던지지 않는다 */
class SlackCollectAlertTest {

    @Test
    void 웹훅이_비어_있으면_로그만_남긴다() {
        assertThatCode(() -> new SlackCollectAlert(RestClient.builder(), "").send("hi"))
                .doesNotThrowAnyException();
    }

    @Test
    void 발송_실패해도_던지지_않는다() {
        assertThatCode(() -> new SlackCollectAlert(RestClient.builder(), "http://127.0.0.1:1/hook").send("hi"))
                .doesNotThrowAnyException();
    }
}
