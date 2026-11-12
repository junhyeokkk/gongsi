package com.gongsi.collect.diff;

import com.gongsi.common.ProductType;

// 이력·이벤트를 남기는 두 계열 - product 계열(예금·적금·주담대·전세)과 신용대출 계열의 테이블·컬럼 이름
public enum ProductFamily {
    PRODUCT("rate_history", "product_option_id", "product_id"),
    CREDIT("credit_rate_history", "credit_loan_option_id", "credit_loan_product_id");

    private final String historyTable;
    private final String optionColumn;
    private final String productColumn;

    ProductFamily(String historyTable, String optionColumn, String productColumn) {
        this.historyTable = historyTable;
        this.optionColumn = optionColumn;
        this.productColumn = productColumn;
    }

    public static ProductFamily of(ProductType type) {
        return type == ProductType.CRED ? CREDIT : PRODUCT;
    }

    public String historyTable() {
        return historyTable;
    }

    public String optionColumn() {
        return optionColumn;
    }

    public String productColumn() {
        return productColumn;
    }
}
