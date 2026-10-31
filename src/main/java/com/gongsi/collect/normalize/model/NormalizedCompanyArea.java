package com.gongsi.collect.normalize.model;

// companySearch optionList 레코드 하나 - 회사의 시·도별 영업점 유무
public record NormalizedCompanyArea(String finCoNo, String areaCode, String areaName, boolean hasBranch) {
}
