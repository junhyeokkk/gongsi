package com.gongsi.collect.normalize.parse;

import com.gongsi.common.GongsiException;

// 레코드 하나를 정규화하지 못함 - 배치는 이 레코드만 격리하고 계속
public class NormalizeException extends GongsiException {

    private final NormalizeErrorReason reason;

    public NormalizeException(NormalizeErrorReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public NormalizeErrorReason reason() {
        return reason;
    }
}
