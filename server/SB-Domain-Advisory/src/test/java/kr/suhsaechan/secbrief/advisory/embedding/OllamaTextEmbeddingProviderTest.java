package kr.suhsaechan.secbrief.advisory.embedding;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Ollama 임베딩 제공자: /api/embed 요청 본문·인증 헤더, embeddings > 벡터 목록, 미설정·꺼짐·2xx 아님·연결 실패·벡터 수 불일치 처리
 * (로컬 서버, 외부 호출 없음)
 */
class OllamaTextEmbeddingProviderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MODEL = "test-embedding-model";
    private static final List<String> TEXTS = List.of("랜섬웨어 조직, 병원 공격\nRansomware gang hits hospital", "개인정보 유출 사고");

    private final AtomicReference<Headers> receivedHeaders = new AtomicReference<>();
    private final AtomicReference<String> receivedMethod = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicReference<String> responseBody =
            new AtomicReference<>("{\"model\":\"test-embedding-model\",\"embeddings\":[[0.25,-0.5,1],[0,0.125,2.5]]}");
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/embed", exchange -> {
            receivedHeaders.set(exchange.getRequestHeaders());
            receivedMethod.set(exchange.getRequestMethod());
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
            respond(exchange, responseStatus.get(), responseBody.get());
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsModelAndInputsAndReturnsVectorsInOrder() throws Exception {
        List<float[]> embeddings = createProvider("").embed(TEXTS);

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0)).containsExactly(0.25f, -0.5f, 1f);
        assertThat(embeddings.get(1)).containsExactly(0f, 0.125f, 2.5f);
        JsonNode embedRequest = OBJECT_MAPPER.readTree(receivedBody.get());
        assertThat(receivedMethod.get()).isEqualTo("POST");
        assertThat(embedRequest.get("model").asText()).isEqualTo(MODEL);
        assertThat(embedRequest.get("input")).hasSize(2);
        assertThat(embedRequest.get("input").get(0).asText()).isEqualTo(TEXTS.get(0));
        assertThat(embedRequest.get("input").get(1).asText()).isEqualTo(TEXTS.get(1));
    }

    @Test
    void sendsBearerAuthorizationOnlyWhenApiKeyIsSet() {
        createProvider("").embed(TEXTS);
        assertThat(receivedHeaders.get().getFirst("Authorization")).isNull();

        createProvider("test-key").embed(TEXTS);
        assertThat(receivedHeaders.get().getFirst("Authorization")).isEqualTo("Bearer test-key");
    }

    @Test
    void acceptsBaseUrlWithTrailingSlashAndReportsModelName() {
        OllamaTextEmbeddingProvider provider = new OllamaTextEmbeddingProvider(true, toLocalBaseUrl() + "/", " " + MODEL + " ", "");

        assertThat(provider.embed(TEXTS)).hasSize(2);
        assertThat(provider.getModelName()).isEqualTo(MODEL);
    }

    @Test
    void isUnavailableWhenDisabledOrWithoutBaseUrlOrModel() {
        OllamaTextEmbeddingProvider disabledProvider = new OllamaTextEmbeddingProvider(false, toLocalBaseUrl(), MODEL, "");
        OllamaTextEmbeddingProvider providerWithoutBaseUrl = new OllamaTextEmbeddingProvider(true, " ", MODEL, "");
        OllamaTextEmbeddingProvider providerWithoutModel = new OllamaTextEmbeddingProvider(true, toLocalBaseUrl(), "", "");
        OllamaTextEmbeddingProvider providerWithTooLongModel =
                new OllamaTextEmbeddingProvider(true, toLocalBaseUrl(), "m".repeat(101), "");

        assertThat(disabledProvider.isAvailable()).isFalse();
        assertThat(providerWithoutBaseUrl.isAvailable()).isFalse();
        assertThat(providerWithoutModel.isAvailable()).isFalse();
        assertThat(providerWithTooLongModel.isAvailable()).isFalse();
        assertThat(createProvider("").isAvailable()).isTrue();
        assertThatThrownBy(() -> disabledProvider.embed(TEXTS)).extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
        assertThat(receivedBody.get()).isNull();
    }

    @Test
    void returnsEmptyWithoutCallingWhenNoTexts() {
        assertThat(createProvider("").embed(List.of())).isEmpty();
        assertThat(receivedBody.get()).isNull();
    }

    @Test
    void throwsAiApiErrorWithStatusWhenNot2xx() {
        responseStatus.set(404);
        responseBody.set("{\"error\":\"model not found\"}");

        assertThatThrownBy(() -> createProvider("test-key").embed(TEXTS))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("status=404")
                .hasMessageNotContaining("test-key")
                .hasMessageNotContaining("127.0.0.1")
                .hasMessageNotContaining("병원")
                .extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
    }

    @Test
    void throwsAiApiErrorWhenEmbeddingCountOrShapeIsWrong() {
        responseBody.set("{\"embeddings\":[[0.1,0.2]]}");
        assertThatThrownBy(() -> createProvider("").embed(TEXTS))
                .hasMessageContaining("embedding count mismatch")
                .extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);

        responseBody.set("{\"embeddings\":[[0.1,0.2],[]]}");
        assertThatThrownBy(() -> createProvider("").embed(TEXTS)).extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);

        responseBody.set("{\"embeddings\":[[0.1,0.2],[0.3,\"x\"]]}");
        assertThatThrownBy(() -> createProvider("").embed(TEXTS)).extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);

        responseBody.set("{\"done\":true}");
        assertThatThrownBy(() -> createProvider("").embed(TEXTS)).extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
    }

    @Test
    void throwsAiApiErrorWhenConnectionFailsOrBaseUrlIsMalformed() {
        OllamaTextEmbeddingProvider provider = createProvider("");
        server.stop(0);

        assertThatThrownBy(() -> provider.embed(TEXTS))
                .isInstanceOf(CustomException.class)
                .hasMessageNotContaining("127.0.0.1")
                .extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
        assertThatThrownBy(() -> new OllamaTextEmbeddingProvider(true, "http://127.0.0.1/bad path", MODEL, "").embed(TEXTS))
                .extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
    }

    private OllamaTextEmbeddingProvider createProvider(String apiKey) {
        return new OllamaTextEmbeddingProvider(true, toLocalBaseUrl(), MODEL, apiKey);
    }

    private String toLocalBaseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bodyBytes = body.getBytes(UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bodyBytes.length);
        exchange.getResponseBody().write(bodyBytes);
        exchange.close();
    }
}
