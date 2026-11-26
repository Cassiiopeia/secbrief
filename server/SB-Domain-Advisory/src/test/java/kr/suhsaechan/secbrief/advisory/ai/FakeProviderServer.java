package kr.suhsaechan.secbrief.advisory.ai;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * 테스트용 제공자 서버 (로컬, 외부 호출 없음): 받은 요청을 기록하고 정해 둔 상태 코드·본문을 돌려줌
 * 응답은 요청마다 고를 수 있음 (키별로 다른 응답: 다음 키 넘김 확인용)
 */
final class FakeProviderServer implements AutoCloseable {

    record ReceivedRequest(String method, String path, Headers headers, String body) {
    }

    record StubResponse(int status, String body) {
    }

    private final HttpServer server;
    private final List<ReceivedRequest> receivedRequests = new CopyOnWriteArrayList<>();
    private volatile Function<ReceivedRequest, StubResponse> responder = request -> new StubResponse(200, "{}");

    FakeProviderServer(String path) {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext(path, exchange -> {
            ReceivedRequest request = new ReceivedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders(), new String(exchange.getRequestBody().readAllBytes(), UTF_8));
            receivedRequests.add(request);
            StubResponse response = responder.apply(request);
            byte[] bodyBytes = response.body().getBytes(UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bodyBytes.length);
            exchange.getResponseBody().write(bodyBytes);
            exchange.close();
        });
        server.start();
    }

    /** 모든 요청에 같은 응답 */
    void respond(int status, String body) {
        this.responder = request -> new StubResponse(status, body);
    }

    /** 요청을 보고 응답을 고름 */
    void respondBy(Function<ReceivedRequest, StubResponse> responder) {
        this.responder = responder;
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<ReceivedRequest> receivedRequests() {
        return receivedRequests;
    }

    ReceivedRequest lastRequest() {
        return receivedRequests.get(receivedRequests.size() - 1);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
