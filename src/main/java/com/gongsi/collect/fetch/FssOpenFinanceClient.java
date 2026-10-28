package com.gongsi.collect.fetch;

import com.gongsi.common.ProductType;
import org.springframework.stereotype.Component;

/** FSS 금융상품 Open API 호출 전담. HTTP 레벨만 책임 (파싱/저장 X) */
@Component
public class FssOpenFinanceClient {

    // TODO 2주차: RestClient 주입. FssApiProperties 로 baseUrl/authKey/timeout 설정
    // TODO 2주차: fetchPage(productType, finGrpNo, pageNo) 구현
    //   - 엔드포인트: /finlifeapi/{상품군별 경로}
    //   - 상품군별 경로 매핑 테이블은 docs/survey/2026-10-28-first-fetch.md 참조
    //   - 응답 JSON 을 그대로 String 으로 리턴 (파싱은 FetchService 몫)
    //   - 네트워크 예외 → FssApiException (GongsiException 상속) 으로 랩

    public String fetchPage(ProductType type, String finGrpNo, int pageNo) {
        throw new UnsupportedOperationException("2주차 구현 예정");
    }
}
