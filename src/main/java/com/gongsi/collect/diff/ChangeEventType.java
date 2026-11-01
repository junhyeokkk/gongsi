package com.gongsi.collect.diff;

// change_event.event_type 값 - 상품 단위(NEW·CLOSED)와 옵션 단위(NEW·RATE_UP·RATE_DOWN·OPTION_REMOVED)
public enum ChangeEventType {
    NEW,
    RATE_UP,
    RATE_DOWN,
    CLOSED,
    OPTION_REMOVED
}
