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
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** FSS 호출 URL 조립, 재시도, 예외 랩핑 검증 */
class FssOpenFinanceClientTest {

    private static final String BASE_URL = "https://fss.test/finlifeapi";
    private static final String AUTH_KEY = "secret-key";
    private static final String DEP_URL = BASE_URL + "/depositProductsSearch.json?auth=secret-key&topFinGrpNo=020000&pageNo=1";

    private MockRestServiceServer server;
    private FssOpenFinanceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FssOpenFinanceClient(builder.build(), props(AUTH_KEY));
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
    void 금융회사는_companySearch_경로로_호출() {
        server.expect(requestTo(BASE_URL + "/companySearch.json?auth=secret-key&topFinGrpNo=020000&pageNo=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"result\":{}}", MediaType.APPLICATION_JSON));

        String body = client.fetchCompanyPage("020000", 1);

        assertThat(body).isEqualTo("{\"result\":{}}");
        server.verify();
    }

    @Test
    void 일시적인_5xx_는_재시도해서_성공하면_본문_반환() {
        server.expect(once(), requestTo(DEP_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo(DEP_URL)).andRespond(withSuccess("{\"result\":{}}", MediaType.APPLICATION_JSON));

        String body = client.fetchPage(ProductType.DEP, "020000", 1);

        assertThat(body).isEqualTo("{\"result\":{}}");
        server.verify();
    }

    @Test
    void 서버_오류가_계속되면_최대_횟수까지만_시도하고_FssApiException() {
        server.expect(times(3), requestTo(DEP_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.fetchPage(ProductType.DEP, "020000", 1))
                .isInstanceOf(FssApiException.class)
                .hasMessageContaining("DEP")
                .hasMessageContaining("3회")
                .hasMessageNotContaining(AUTH_KEY);
        server.verify();
    }

    @Test
    void 네트워크_오류도_재시도하고_끝내_실패하면_인증키를_노출하지_않음() {
        server.expect(times(3), requestTo(DEP_URL))
                .andRespond(withException(new IOException("connect timed out auth=secret-key")));

        assertThatThrownBy(() -> client.fetchPage(ProductType.DEP, "020000", 1))
                .isInstanceOf(FssApiException.class)
                .hasMessageNotContaining(AUTH_KEY)
                .hasNoCause();
        server.verify();
    }

    @Test
    void 요청_오류_4xx_는_다시_해도_같으므로_재시도하지_않음() {
        server.expect(once(), requestTo(DEP_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.fetchPage(ProductType.DEP, "020000", 1))
                .isInstanceOf(FssApiException.class)
                .hasMessageContaining("1회");
        server.verify();
    }

    @Test
    void 인증키가_비어_있으면_호출_전에_실패() {
        var noKey = new FssOpenFinanceClient(RestClient.create(), props(""));

        assertThatThrownBy(() -> noKey.fetchPage(ProductType.DEP, "020000", 1))
                .isInstanceOf(FssApiException.class)
                .hasMessageContaining("FINLIFE_API_KEY");
    }

    // 테스트에서는 재시도 대기를 0 으로 둔다
    private static FssApiProperties props(String authKey) {
        return new FssApiProperties(BASE_URL, authKey, 1000, 3, 0);
    }
}
