package com.gongsi.common;

/** 도메인 예외 루트. 외부 API/정규화/조회 레이어 모두 이걸 상속한다 */
public class GongsiException extends RuntimeException {
    public GongsiException(String message) { super(message); }
    public GongsiException(String message, Throwable cause) { super(message, cause); }
}
