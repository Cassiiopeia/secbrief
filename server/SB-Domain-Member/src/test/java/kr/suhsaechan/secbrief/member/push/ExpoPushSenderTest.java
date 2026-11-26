package kr.suhsaechan.secbrief.member.push;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Expo 발송기: 티켓 대응, 무효 토큰, 요청 오류, 다른 프로젝트 토큰 가려내기, 100건 나누기, 접수증 (로컬 서버, 외부 호출 없음)
 */
class ExpoPushSenderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String OWN_EXPERIENCE_ID = "@secbrief-owner/secbrief";

    private final List<JsonNode> receivedSendBodies = new CopyOnWriteArrayList<>();
    private final List<String> receivedAuthorizationHeaders = new CopyOnWriteArrayList<>();
    // 발송 요청 본문 > 응답 (상태 코드, JSON)
    private Function<JsonNode, StubResponse> sendResponder = requestBody -> toOkTickets(requestBody.size());
    private String receiptsResponseJson = "{\"data\":{}}";
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/--/api/v2/push/send", exchange -> {
            JsonNode requestBody = OBJECT_MAPPER.readTree(exchange.getRequestBody().readAllBytes());
            receivedSendBodies.add(requestBody);
            receivedAuthorizationHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            StubResponse stubResponse = sendResponder.apply(requestBody);
            respond(exchange, stubResponse.statusCode(), stubResponse.json());
        });
        server.createContext("/--/api/v2/push/getReceipts", exchange -> respond(exchange, 200, receiptsResponseJson));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void mapsTicketsToMessagesInOrder() {
        sendResponder = requestBody -> new StubResponse(200, """
                {"data":[
                  {"status":"ok","id":"ticket-1"},
                  {"status":"error","message":"not registered","details":{"error":"DeviceNotRegistered"}},
                  {"status":"error","message":"too big","details":{"error":"MessageTooBig"}}
                ]}""");

        List<PushSendOutcome> outcomes = createSender("").send(createMessages(3));

        assertThat(outcomes).containsExactly(
                PushSendOutcome.accepted(toToken(0), "ticket-1"),
                PushSendOutcome.deviceNotRegistered(toToken(1)),
                PushSendOutcome.failed(toToken(2)));
    }

    @Test
    void sendsMessageFieldsAndNoAuthorizationWithoutAccessToken() {
        createSender("").send(createMessages(1));

        JsonNode sentMessage = receivedSendBodies.get(0).get(0);
        assertThat(sentMessage.path("to").asText()).isEqualTo(toToken(0));
        assertThat(sentMessage.path("title").asText()).isEqualTo("제목 0");
        assertThat(sentMessage.path("body").asText()).isEqualTo("본문 0");
        assertThat(sentMessage.path("data").path("targetTab").asText()).isEqualTo("vulnerability");
        assertThat(sentMessage.path("channelId").asText()).isEqualTo("urgent-alerts");
        assertThat(sentMessage.path("priority").asText()).isEqualTo("high");
        assertThat(receivedAuthorizationHeaders).containsExactly("null");
    }

    @Test
    void sendsBearerAccessTokenWhenConfigured() {
        createSender("expo-access-token").send(createMessages(1));

        assertThat(receivedAuthorizationHeaders).containsExactly("Bearer expo-access-token");
    }

    @Test
    void acceptsSingleTicketObjectForSingleMessage() {
        sendResponder = requestBody -> new StubResponse(200, "{\"data\":{\"status\":\"ok\",\"id\":\"ticket-1\"}}");

        assertThat(createSender("").send(createMessages(1)))
                .containsExactly(PushSendOutcome.accepted(toToken(0), "ticket-1"));
    }

    @Test
    void splitsMessagesIntoRequestsOfOneHundred() {
        List<PushSendOutcome> outcomes = createSender("").send(createMessages(250));

        assertThat(receivedSendBodies).extracting(JsonNode::size).containsExactly(100, 100, 50);
        assertThat(outcomes).hasSize(250).allMatch(outcome -> outcome.status() == PushSendStatus.ACCEPTED);
        assertThat(outcomes.get(249).pushToken()).isEqualTo(toToken(249));
    }

    @Test
    void failsWholeChunkOnRequestErrorAndKeepsOtherChunks() {
        // 첫 묶음만 서버 오류
        sendResponder = requestBody -> receivedSendBodies.size() == 1
                ? new StubResponse(500, "{\"errors\":[{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"down\"}]}")
                : toOkTickets(requestBody.size());

        List<PushSendOutcome> outcomes = createSender("").send(createMessages(150));

        assertThat(outcomes.subList(0, 100)).allMatch(outcome -> outcome.status() == PushSendStatus.FAILED);
        assertThat(outcomes.subList(100, 150)).allMatch(outcome -> outcome.status() == PushSendStatus.ACCEPTED);
    }

    @Test
    void failsChunkWhenResponseIsNotJsonOrTicketCountDiffers() {
        sendResponder = requestBody -> new StubResponse(200, "<html>gateway</html>");
        assertThat(createSender("").send(createMessages(2))).allMatch(outcome -> outcome.status() == PushSendStatus.FAILED);

        sendResponder = requestBody -> toOkTickets(1);
        assertThat(createSender("").send(createMessages(2))).allMatch(outcome -> outcome.status() == PushSendStatus.FAILED);
    }

    @Test
    void failsAllWhenServerIsUnreachable() {
        ExpoPushSender sender = createSender("");
        server.stop(0);

        assertThat(sender.send(createMessages(2))).allMatch(outcome -> outcome.status() == PushSendStatus.FAILED);
    }

    @Test
    void dropsForeignProjectTokensAndResendsOwnTokens() {
        // 첫 요청: 프로젝트 섞임 오류 (토큰 1 이 남의 프로젝트), 둘째 요청: 나머지 접수
        sendResponder = requestBody -> receivedSendBodies.size() == 1
                ? new StubResponse(400, """
                        {"errors":[{"code":"PUSH_TOO_MANY_EXPERIENCE_IDS","message":"mixed","details":{
                          "@secbrief-owner/secbrief":["%s","%s"],
                          "@someone/other-app":["%s"]}}]}""".formatted(toToken(0), toToken(2), toToken(1)))
                : toOkTickets(requestBody.size());

        List<PushSendOutcome> outcomes = createSender("").send(createMessages(3));

        assertThat(receivedSendBodies).hasSize(2);
        assertThat(receivedSendBodies.get(1)).extracting(message -> message.path("to").asText())
                .containsExactly(toToken(0), toToken(2));
        assertThat(outcomes).extracting(PushSendOutcome::status).containsExactly(
                PushSendStatus.ACCEPTED, PushSendStatus.DEVICE_NOT_REGISTERED, PushSendStatus.ACCEPTED);
    }

    @Test
    void treatsLargestProjectAsOwnWhenConfiguredProjectIsAbsent() {
        // 설정한 프로젝트 이름이 응답에 없음 > 토큰이 많은 쪽을 우리 것으로 봄 (설정 오타로 전체 기기를 지우지 않게)
        sendResponder = requestBody -> receivedSendBodies.size() == 1
                ? new StubResponse(400, """
                        {"errors":[{"code":"PUSH_TOO_MANY_EXPERIENCE_IDS","message":"mixed","details":{
                          "@renamed-owner/secbrief":["%s","%s"],
                          "@someone/other-app":["%s"]}}]}""".formatted(toToken(0), toToken(1), toToken(2)))
                : toOkTickets(requestBody.size());

        List<PushSendOutcome> outcomes = createSender("").send(createMessages(3));

        assertThat(outcomes).extracting(PushSendOutcome::status).containsExactly(
                PushSendStatus.ACCEPTED, PushSendStatus.ACCEPTED, PushSendStatus.DEVICE_NOT_REGISTERED);
    }

    @Test
    void mapsReceiptsAndOmitsTicketsWithoutReceipt() {
        receiptsResponseJson = """
                {"data":{
                  "ticket-ok":{"status":"ok"},
                  "ticket-gone":{"status":"error","message":"gone","details":{"error":"DeviceNotRegistered"}},
                  "ticket-credentials":{"status":"error","message":"bad","details":{"error":"InvalidCredentials"}}
                }}""";

        Map<String, PushReceipt> receipts = createSender("")
                .fetchReceipts(List.of("ticket-ok", "ticket-gone", "ticket-credentials", "ticket-waiting"));

        assertThat(receipts).containsOnlyKeys("ticket-ok", "ticket-gone", "ticket-credentials");
        assertThat(receipts.get("ticket-ok").status()).isEqualTo(PushSendStatus.ACCEPTED);
        assertThat(receipts.get("ticket-gone").status()).isEqualTo(PushSendStatus.DEVICE_NOT_REGISTERED);
        assertThat(receipts.get("ticket-credentials"))
                .isEqualTo(new PushReceipt(PushSendStatus.FAILED, "InvalidCredentials"));
    }

    @Test
    void isUnavailableWhenDisabled() {
        ExpoPushSender disabledSender = new ExpoPushSender(false, toBaseUrl(), "", OWN_EXPERIENCE_ID, OBJECT_MAPPER);

        assertThat(disabledSender.isAvailable()).isFalse();
        assertThat(createSender("").isAvailable()).isTrue();
    }

    private ExpoPushSender createSender(String accessToken) {
        return new ExpoPushSender(true, toBaseUrl(), accessToken, OWN_EXPERIENCE_ID, OBJECT_MAPPER);
    }

    private String toBaseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    private static List<PushMessage> createMessages(int messageCount) {
        return IntStream.range(0, messageCount)
                .mapToObj(index -> new PushMessage(toToken(index), "제목 " + index, "본문 " + index,
                        Map.of("targetTab", "vulnerability"), "urgent-alerts"))
                .toList();
    }

    private static String toToken(int index) {
        return "ExponentPushToken[device-" + index + "]";
    }

    private static StubResponse toOkTickets(int ticketCount) {
        List<String> tickets = new ArrayList<>();
        for (int index = 0; index < ticketCount; index++) {
            tickets.add("{\"status\":\"ok\",\"id\":\"ticket-" + index + "\"}");
        }
        return new StubResponse(200, "{\"data\":[" + String.join(",", tickets) + "]}");
    }

    private static void respond(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] responseBytes = json.getBytes(UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }

    private record StubResponse(int statusCode, String json) {
    }
}
