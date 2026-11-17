package com.gongsi.collect.job;

/** 수집 결과 알림 채널 - 보내다 실패해도 던지지 않는다 */
@FunctionalInterface
public interface CollectAlert {
    void send(String text);
}
