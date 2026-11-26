package kr.suhsaechan.secbrief.advisory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchRuntimeException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * HTTP 제공자 공통 호출 (로컬 가짜 서버): JSON POST·헤더·응답 읽기, 상태 코드·연결 실패·주소 오류 > 실패 종류, 주소 잇기, 토큰 수 읽기
 */
class ProviderHttpClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PATH = "/v1/test";

    private final FakeProviderServer server = new FakeProviderServer(PATH);
    private final ProviderHttpClient providerHttpClient = new ProviderHttpClient();

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void postsJsonBodyWithGivenHeadersAndReturnsResponseJson() throws Exception {
        server.respond(200, "{\"status\":\"completed\"}");

        JsonNode responseBody = providerHttpClient.postJson(server.baseUrl() + PATH,
                headers -> headers.set("x-test-key", "test-key-value"), Map.of("model", "test-model"));

        assertThat(responseBody.path("status").asText()).isEqualTo("completed");
        assertThat(server.lastRequest().method()).isEqualTo("POST");
        assertThat(server.lastRequest().headers().getFirst("x-test-key")).isEqualTo("test-key-value");
        assertThat(server.lastRequest().headers().getFirst("Content-Type")).startsWith("application/json");
        assertThat(OBJECT_MAPPER.readTree(server.lastRequest().body()).get("model").asText()).isEqualTo("test-model");
    }

    @Test
    void returnsMissingNodeInsteadOfNullForEmptyBody() {
        server.respond(200, "");

        assertThat(post().isMissingNode()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"400, BAD_REQUEST", "401, UNAUTHORIZED", "402, PAYMENT_REQUIRED", "403, PERMISSION_DENIED",
            "404, NOT_FOUND", "429, RATE_LIMITED", "500, SERVER_ERROR", "503, SERVER_ERROR"})
    void classifiesErrorResponseByStatusCode(int status, AiFailureKind expectedFailureKind) {
        server.respond(status, "{\"error\":{\"message\":\"failed\"}}");

        assertThat(ProviderHttpClient.classify(catchRuntimeException(this::post))).isEqualTo(expectedFailureKind);
    }

    @Test
    void classifiesConnectionFailureMalformedUrlAndOtherFailures() {
        server.close();
        assertThat(ProviderHttpClient.classify(catchRuntimeException(this::post))).isEqualTo(AiFailureKind.CONNECTION_FAILED);

        RuntimeException malformedUrlFailure = catchRuntimeException(() ->
                providerHttpClient.postJson("http://127.0.0.1/bad path", headers -> { }, Map.of()));
        assertThat(ProviderHttpClient.classify(malformedUrlFailure)).isEqualTo(AiFailureKind.INVALID_CONFIGURATION);
        assertThat(ProviderHttpClient.classify(new IllegalStateException("other"))).isEqualTo(AiFailureKind.UNKNOWN);
    }

    @Test
    void classifiesNonJsonSuccessBodyAsConnectionFailure() {
        server.respond(200, "<html>gateway</html>");

        assertThat(ProviderHttpClient.classify(catchRuntimeException(this::post))).isEqualTo(AiFailureKind.CONNECTION_FAILED);
    }

    @Test
    void joinsBaseUrlAndPathWithoutDoubleSlash() {
        assertThat(ProviderHttpClient.toEndpointUrl("https://api.example.com/", "/v1/test")).isEqualTo("https://api.example.com/v1/test");
        assertThat(ProviderHttpClient.toEndpointUrl(" https://api.example.com ", "/v1/test")).isEqualTo("https://api.example.com/v1/test");
    }

    @Test
    void readsTokenCountOnlyFromIntegralNumbers() throws Exception {
        JsonNode usage = OBJECT_MAPPER.readTree("{\"input_tokens\":812,\"output_tokens\":\"96\"}");

        assertThat(ProviderHttpClient.toTokenCount(usage.path("input_tokens"))).isEqualTo(812);
        assertThat(ProviderHttpClient.toTokenCount(usage.path("output_tokens"))).isNull();
        assertThat(ProviderHttpClient.toTokenCount(usage.path("missing"))).isNull();
    }

    private JsonNode post() {
        return providerHttpClient.postJson(server.baseUrl() + PATH, headers -> { }, Map.of("model", "test-model"));
    }
}
