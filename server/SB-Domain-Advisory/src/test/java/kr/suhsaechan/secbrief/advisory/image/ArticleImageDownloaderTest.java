package kr.suhsaechan.secbrief.advisory.image;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 이미지 내려받기: 본문 바이트, 리다이렉트(따라가기·3회 초과·내부 주소로 이동), 종류 불일치, 크기 초과, 스킴, 주소 차단
 * 로컬 서버만 씀 (외부 호출 없음): 로컬 서버 접속은 루프백을 허용하는 검사기로, 차단 확인은 운영 검사기로
 */
class ArticleImageDownloaderTest {

    private static final byte[] IMAGE_BYTES = "그림 바이트".getBytes(UTF_8);
    private static final String IMAGE_CONTENT_TYPE = "image/png";

    // 로컬 가짜 서버용: 루프백 IPv4 만 허용 (그 밖의 규칙은 운영과 같게 전부 거부)
    private final ArticleImageDownloader loopbackAllowedDownloader = new ArticleImageDownloader(
            address -> address.equals(InetAddress.getLoopbackAddress()));
    private final ArticleImageDownloader productionDownloader =
            new ArticleImageDownloader(new PublicImageHostAddressValidator());
    private final AtomicInteger receivedRequestCount = new AtomicInteger();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/image", exchange -> respond(exchange, 200, IMAGE_CONTENT_TYPE, IMAGE_BYTES));
        server.createContext("/moved", exchange -> redirect(exchange, "/image"));
        server.createContext("/loop", exchange -> redirect(exchange, "/loop"));
        server.createContext("/to-metadata", exchange -> redirect(exchange, "http://169.254.169.254/latest/meta-data/"));
        server.createContext("/to-file", exchange -> redirect(exchange, "file:///etc/passwd"));
        server.createContext("/html", exchange -> respond(exchange, 200, "text/html; charset=utf-8", IMAGE_BYTES));
        server.createContext("/missing", exchange -> respond(exchange, 404, IMAGE_CONTENT_TYPE, IMAGE_BYTES));
        server.createContext("/empty", exchange -> respond(exchange, 200, IMAGE_CONTENT_TYPE, new byte[0]));
        // 크기를 밝힌 큰 응답 (Content-Length 로 바로 거부)
        server.createContext("/large-declared", exchange -> respond(exchange, 200, IMAGE_CONTENT_TYPE,
                new byte[ArticleImageDownloader.MAXIMUM_IMAGE_BYTES + 1]));
        // 크기를 숨긴 큰 응답 (chunked: 읽다가 상한에서 멈춤)
        server.createContext("/large-chunked", exchange -> respondChunked(exchange,
                new byte[ArticleImageDownloader.MAXIMUM_IMAGE_BYTES + 1]));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void returnsImageBytes() {
        assertThat(loopbackAllowedDownloader.download(toLocalUrl("/image"))).isEqualTo(IMAGE_BYTES);
    }

    @Test
    void followsRedirectToImage() {
        assertThat(loopbackAllowedDownloader.download(toLocalUrl("/moved"))).isEqualTo(IMAGE_BYTES);
        assertThat(receivedRequestCount.get()).isEqualTo(2);
    }

    @Test
    void stopsAfterThreeRedirects() {
        assertRejected(loopbackAllowedDownloader, toLocalUrl("/loop"), "too many redirects");
        // 첫 요청 1 + 리다이렉트 3
        assertThat(receivedRequestCount.get()).isEqualTo(1 + ArticleImageDownloader.MAXIMUM_REDIRECT_COUNT);
    }

    @Test
    void checksAddressAgainOnRedirect() {
        assertRejected(loopbackAllowedDownloader, toLocalUrl("/to-metadata"), "blocked address");
    }

    @Test
    void rejectsRedirectToNonHttpScheme() {
        assertRejected(loopbackAllowedDownloader, toLocalUrl("/to-file"), "invalid redirect");
    }

    @Test
    void rejectsNonImageContentType() {
        assertRejected(loopbackAllowedDownloader, toLocalUrl("/html"), "not an image");
    }

    @Test
    void rejectsNonSuccessStatus() {
        assertRejected(loopbackAllowedDownloader, toLocalUrl("/missing"), "status=404");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/large-declared", "/large-chunked", "/empty"})
    void rejectsTooLargeOrEmptyBody(String path) {
        assertRejected(loopbackAllowedDownloader, toLocalUrl(path), "too large or empty");
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "ftp://images.example.test/a.jpg", "gopher://127.0.0.1/", "not a url", ""})
    void rejectsNonHttpUrl(String imageUrl) {
        assertRejected(productionDownloader, imageUrl, "invalid url");
    }

    @Test
    void rejectsUrlWithCredentials() {
        assertRejected(loopbackAllowedDownloader, "http://user:secret@127.0.0.1:" + server.getAddress().getPort() + "/image",
                "url with credentials");
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "[::1]", "[::ffff:127.0.0.1]", "localhost"})
    void productionValidatorBlocksLocalServer(String host) {
        // IP 를 직접 적은 주소는 요청 전 검사, localhost 는 DNS 조회 결과 검사
        assertRejected(productionDownloader, "http://" + host + ":" + server.getAddress().getPort() + "/image", "blocked address");
        assertThat(receivedRequestCount.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.1/image", "http://2130706433/image", "http://10.0.0.1/a.jpg", "http://192.168.0.10:9000/a.jpg",
            "http://169.254.169.254/latest/meta-data/"})
    void productionValidatorRejectsInternalAddressForms(String imageUrl) {
        assertThatThrownBy(() -> productionDownloader.download(imageUrl))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
        assertThat(receivedRequestCount.get()).isZero();
    }

    private static void assertRejected(ArticleImageDownloader downloader, String imageUrl, String expectedDetail) {
        assertThatThrownBy(() -> downloader.download(imageUrl))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException = (CustomException) exception;
                    assertThat(customException.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
                    assertThat(customException.getDetail()).isEqualTo(expectedDetail);
                });
    }

    private String toLocalUrl(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        respond(exchange, 302, "text/plain", new byte[0]);
    }

    private void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        receivedRequestCount.incrementAndGet();
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(body);
        } catch (IOException ignored) {
            // 내려받는 쪽이 크기 초과로 끊은 경우
        }
    }

    private void respondChunked(HttpExchange exchange, byte[] body) throws IOException {
        receivedRequestCount.incrementAndGet();
        exchange.getResponseHeaders().add("Content-Type", IMAGE_CONTENT_TYPE);
        // 길이 0: chunked 전송 (Content-Length 없음)
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(body);
        } catch (IOException ignored) {
            // 내려받는 쪽이 상한에서 끊은 경우
        }
    }
}
