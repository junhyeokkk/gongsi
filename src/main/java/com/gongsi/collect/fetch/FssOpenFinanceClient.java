package com.gongsi.collect.fetch;

import com.gongsi.common.FssApiProperties;
import com.gongsi.common.ProductType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// FSS 금융상품 Open API 호출 전담
@Component
public class FssOpenFinanceClient {

    private static final Logger log = LoggerFactory.getLogger(FssOpenFinanceClient.class);

    private final RestClient fssRestClient;
    private final FssApiProperties props;

    public FssOpenFinanceClient(RestClient fssRestClient, FssApiProperties props) {
        this.fssRestClient = fssRestClient;
        this.props = props;
    }

    // 상품군/금융권/페이지 한 장을 호출해 응답 JSON 으로 반환
    public String fetchPage(ProductType type, String finGrpNo, int pageNo) {
        if (props.authKey() == null || props.authKey().isBlank()) {
            throw new FssApiException("FSS 인증키 없음 — 환경변수 FINLIFE_API_KEY 를 설정할 것");
        }
        for (int attempt = 1; ; attempt++) { // 일시 오류는 maxAttempts 까지 재시도
            try {
                return call(type, finGrpNo, pageNo);
            } catch (ResourceAccessException | HttpServerErrorException e) {
                // 네트워크 오류·타임아웃·5xx 는 일시적일 수 있어 재시도
                if (attempt >= props.maxAttempts()) {
                    throw failure(type, finGrpNo, pageNo, attempt, e);
                }
                log.warn("FSS 재시도 {}/{} type={} finGrpNo={} page={}: {}", attempt, props.maxAttempts(),
                        type, finGrpNo, pageNo, mask(e.getMessage()));
                sleep(props.retryBackoffMs() << (attempt - 1));
            } catch (RestClientException e) {
                // 4xx 등은 다시 보내도 같은 결과라 바로 실패
                throw failure(type, finGrpNo, pageNo, attempt, e);
            }
        }
    }

    private String call(ProductType type, String finGrpNo, int pageNo) {
        return fssRestClient.get()
                .uri(uri -> uri.path("/{service}.json")
                        .queryParam("auth", props.authKey())
                        .queryParam("topFinGrpNo", finGrpNo)
                        .queryParam("pageNo", pageNo)
                        .build(serviceName(type)))
                .retrieve()
                .body(String.class);
    }

    // 원인 메시지에 요청 URL(인증키 포함)이 들어가므로 마스킹하고 cause 는 잇지 않는다
    private FssApiException failure(ProductType type, String finGrpNo, int pageNo, int attempts, RestClientException e) {
        return new FssApiException("FSS 호출 실패 type=%s finGrpNo=%s page=%d (%d회 시도): %s".formatted(
                type, finGrpNo, pageNo, attempts, mask(e.getClass().getSimpleName() + " " + e.getMessage())));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FssApiException("FSS 재시도 대기 중 인터럽트");
        }
    }

    // 인증키를 *** 로 바꿈
    private String mask(String text) {
        return text.replace(props.authKey(), "***");
    }

    // ENUM을 API 경로로 변환
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
