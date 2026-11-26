package kr.suhsaechan.secbrief.advisory.collector;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 출처 호출: 본문 바이트·헤더, 2xx 아님·연결 실패 > EXTERNAL_API_ERROR, 조건부 GET 의 304·새 검증값 (로컬 서버, 외부 호출 없음)
 */
class FeedHttpClientTest {

    private static final String FEED_ETAG = "\"feed-v1\"";
    private static final String FEED_LAST_MODIFIED = "Wed, 25 Nov 2026 00:00:00 GMT";

    private final FeedHttpClient feedHttpClient = new FeedHttpClient();
    private final AtomicReference<Headers> receivedHeaders = new AtomicReference<>();
    private final AtomicReference<String> receivedRawQuery = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ok", exchange -> {
            receivedHeaders.set(exchange.getRequestHeaders());
            receivedRawQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, "피드 본문");
        });
        server.createContext("/unavailable", exchange -> respond(exchange, 503, "busy"));
        server.createContext("/moved", exchange -> {
            exchange.getResponseHeaders().add("Location", "/ok");
            respond(exchange, 302, "");
        });
        // 검증값이 맞으면 304, 아니면 본문 + 검증값
        server.createContext("/conditional", exchange -> {
            receivedHeaders.set(exchange.getRequestHeaders());
            exchange.getResponseHeaders().add("ETag", FEED_ETAG);
            exchange.getResponseHeaders().add("Last-Modified", FEED_LAST_MODIFIED);
            boolean isSameEtag = FEED_ETAG.equals(exchange.getRequestHeaders().getFirst("If-None-Match"));
            respond(exchange, isSameEtag ? 304 : 200, isSameEtag ? "" : "피드 본문");
        });
        // 검증값 헤더 없이 304 만 주는 출처
        server.createContext("/not-modified-without-validators", exchange -> respond(exchange, 304, ""));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void returnsBodyBytesAndSendsCollectorUserAgent() {
        byte[] body = feedHttpClient.fetch(toLocalUrl("/ok"));

        assertThat(new String(body, UTF_8)).isEqualTo("피드 본문");
        assertThat(receivedHeaders.get().getFirst("User-Agent")).startsWith("secbrief-collector/1.0 (+https://");
    }

    @Test
    void sendsExtraHeaders() {
        feedHttpClient.fetch(toLocalUrl("/ok"), Map.of("Accept", "application/vnd.github+json"));

        assertThat(receivedHeaders.get().getFirst("Accept")).isEqualTo("application/vnd.github+json");
    }

    @Test
    void sendsAlreadyEncodedQueryWithoutEncodingTwice() {
        feedHttpClient.fetch(toLocalUrl("/ok?pubStartDate=2026-11-16T00%3A00%3A00.000&q=a%2Bb"));

        assertThat(receivedRawQuery.get()).isEqualTo("pubStartDate=2026-11-16T00%3A00%3A00.000&q=a%2Bb");
    }

    @Test
    void throwsExternalApiErrorWhenUrlIsMalformed() {
        assertThatThrownBy(() -> feedHttpClient.fetch("http://127.0.0.1/bad path"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    void followsRedirect() {
        assertThat(new String(feedHttpClient.fetch(toLocalUrl("/moved")), UTF_8)).isEqualTo("피드 본문");
    }

    @Test
    void throwsExternalApiErrorWithStatusWhenNot2xx() {
        assertThatThrownBy(() -> feedHttpClient.fetch(toLocalUrl("/unavailable")))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("status=503")
                .extracting("errorCode").isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    void throwsExternalApiErrorWhenConnectionFails() {
        String closedPortUrl = toLocalUrl("/ok");
        server.stop(0);

        assertThatThrownBy(() -> feedHttpClient.fetch(closedPortUrl))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    void conditionalFetchWithoutPreviousValidatorsReturnsBodyAndNewValidators() {
        FeedFetchResult result = feedHttpClient.fetchIfChanged(toLocalUrl("/conditional"), FeedValidators.NONE);

        assertThat(result.isNotModified()).isFalse();
        assertThat(new String(result.body(), UTF_8)).isEqualTo("피드 본문");
        assertThat(result.validators()).isEqualTo(new FeedValidators(FEED_ETAG, FEED_LAST_MODIFIED));
        assertThat(receivedHeaders.get().containsKey("If-None-Match")).isFalse();
        assertThat(receivedHeaders.get().containsKey("If-Modified-Since")).isFalse();
    }

    @Test
    void conditionalFetchSendsPreviousValidatorsAndReturnsNotModifiedOn304() {
        FeedFetchResult result = feedHttpClient.fetchIfChanged(toLocalUrl("/conditional"),
                new FeedValidators(FEED_ETAG, FEED_LAST_MODIFIED));

        assertThat(result.isNotModified()).isTrue();
        assertThat(result.body()).isNull();
        assertThat(result.validators()).isEqualTo(new FeedValidators(FEED_ETAG, FEED_LAST_MODIFIED));
        assertThat(receivedHeaders.get().getFirst("If-None-Match")).isEqualTo(FEED_ETAG);
        assertThat(receivedHeaders.get().getFirst("If-Modified-Since")).isEqualTo(FEED_LAST_MODIFIED);
    }

    @Test
    void conditionalFetchReturnsBodyWhenValidatorIsStale() {
        FeedFetchResult result = feedHttpClient.fetchIfChanged(toLocalUrl("/conditional"),
                new FeedValidators("\"feed-v0\"", null));

        assertThat(result.isNotModified()).isFalse();
        assertThat(result.validators().etag()).isEqualTo(FEED_ETAG);
        assertThat(receivedHeaders.get().containsKey("If-Modified-Since")).isFalse();
    }

    @Test
    void notModifiedWithoutResponseValidatorsKeepsPreviousValidators() {
        FeedValidators previousValidators = new FeedValidators(null, FEED_LAST_MODIFIED);

        FeedFetchResult result = feedHttpClient.fetchIfChanged(toLocalUrl("/not-modified-without-validators"), previousValidators);

        assertThat(result.isNotModified()).isTrue();
        assertThat(result.validators()).isEqualTo(previousValidators);
    }

    @Test
    void plainFetchTreats304AsError() {
        assertThatThrownBy(() -> feedHttpClient.fetch(toLocalUrl("/not-modified-without-validators")))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("status=304");
    }

    private String toLocalUrl(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bodyBytes = body.getBytes(UTF_8);
        exchange.sendResponseHeaders(status, bodyBytes.length == 0 ? -1 : bodyBytes.length);
        if (bodyBytes.length > 0) {
            exchange.getResponseBody().write(bodyBytes);
        }
        exchange.close();
    }
}
