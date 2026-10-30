package com.gongsi.collect.fetch;

import com.gongsi.common.FssApiProperties;
import com.gongsi.common.ProductType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** FSS 금융상품 Open API 호출 전담. HTTP 레벨만 책임 (파싱/저장 X) */
@Component
public class FssOpenFinanceClient {

    private final RestClient fssRestClient;
    private final FssApiProperties props;

    public FssOpenFinanceClient(RestClient fssRestClient, FssApiProperties props) {
        this.fssRestClient = fssRestClient;
        this.props = props;
    }

    /** 상품군·금융권·페이지 한 장을 호출해 응답 JSON 원문 반환 */
    public String fetchPage(ProductType type, String finGrpNo, int pageNo) {
        if (props.authKey() == null || props.authKey().isBlank()) {
            throw new FssApiException("FSS 인증키 없음 — 환경변수 FINLIFE_API_KEY 를 설정할 것");
        }
        try {
            return fssRestClient.get()
                    .uri(uri -> uri.path("/{service}.json")
                            .queryParam("auth", props.authKey())
                            .queryParam("topFinGrpNo", finGrpNo)
                            .queryParam("pageNo", pageNo)
                            .build(serviceName(type)))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            // 원인 메시지에 요청 URL(인증키 포함)이 들어가므로 마스킹하고 cause 는 잇지 않는다
            throw new FssApiException("FSS 호출 실패 type=%s finGrpNo=%s page=%d: %s".formatted(
                    type, finGrpNo, pageNo, mask(e.getClass().getSimpleName() + " " + e.getMessage())));
        }
    }

    private String mask(String text) {
        return text.replace(props.authKey(), "***");
    }

    private static String serviceName(ProductType type) {
        return switch (type) {
            case DEP -> "depositProductsSearch";
            case SAV -> "savingProductsSearch";
            case MORT -> "mortgageLoanProductsSearch";
            case RENT -> "rentHouseLoanProductsSearch";
            case CRED -> "creditLoanProductsSearch";
        };
    }
}
