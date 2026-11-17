package com.gongsi.collect.diff;

import com.gongsi.common.GongsiException;

/** 상품이 한꺼번에 사라지거나 생긴 급변 - 원문 누락 의심이라 다시 돌려도 같은 결과라 재시도하지 않는다 */
public class MassChangeException extends GongsiException {
    public MassChangeException(String message) { super(message); }
}
