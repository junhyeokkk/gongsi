package com.gongsi.collect.fetch;

import com.gongsi.common.GongsiException;

/** FSS Open API 호출 실패 (HTTP 오류·네트워크 오류·err_cd 비정상) */
public class FssApiException extends GongsiException {
    public FssApiException(String message) { super(message); }
}
