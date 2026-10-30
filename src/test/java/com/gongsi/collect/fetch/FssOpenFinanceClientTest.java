package com.gongsi.collect.fetch;

import com.gongsi.common.FssApiProperties;
import com.gongsi.common.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** FSS 호출 URL 조립과 예외 랩핑 검증 */
class FssOpenFinanceClientTest {

    private static final String BASE_URL = "https://fss.test/finlifeapi";
    private static final String AUTH_KEY = "secret-key";

    private MockRestServiceServer server;
    private FssOpenFinanceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FssOpenFinanceClient(builder.build(), new FssApiProperties(BASE_URL, AUTH_KEY, 1000));
    }

    @Test
    void 상품군별_서비스_경로와_쿼리로_호출하고_본문을_그대로_반환() {
        server.expect(requestTo(BASE_URL + "/savingProductsSearch.json?auth=secret-key&topFinGrpNo=030300&pageNo=2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"result\":{}}", MediaType.APPLICATION_JSON));

        String body = client.fetchPage(ProductType.SAV, "030300", 2);

        assertThat(body).isEqualTo("{\"result\":{}}");
        server.verify();
    }

    @Test
    void HTTP_오류는_FssApiException_으로_랩하고_인증키를_노출하지_않음() {
        server.expect(requestTo(BASE_URL + "/depositProductsSearch.json?auth=secret-key&topFinGrpNo=020000&pageNo=1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.fetchPage(ProductType.DEP, "020000", 1))
                .isInstanceOf(FssApiException.class)
                .hasMessageContaining("DEP")
                .hasMessageNotContaining(AUTH_KEY);
    }

    @Test
    void 네트워크_오류는_FssApiException_으로_랩하고_인증키를_노출하지_않음() {
        server.expect(requestTo(BASE_URL + "/creditLoanProductsSearch.json?auth=secret-key&topFinGrpNo=020000&pageNo=1"))
                .andRespond(withException(new IOException("connect timed out auth=secret-key")));

        assertThatThrownBy(() -> client.fetchPage(ProductType.CRED, "020000", 1))
                .isInstanceOf(FssApiException.class)
                .hasMessageNotContaining(AUTH_KEY)
                .hasNoCause();
    }

    @Test
    void 인증키가_비어_있으면_호출_전에_실패() {
        var noKey = new FssOpenFinanceClient(RestClient.create(), new FssApiProperties(BASE_URL, "", 1000));

        assertThatThrownBy(() -> noKey.fetchPage(ProductType.DEP, "020000", 1))
                .isInstanceOf(FssApiException.class)
                .hasMessageContaining("FINLIFE_API_KEY");
    }
}
