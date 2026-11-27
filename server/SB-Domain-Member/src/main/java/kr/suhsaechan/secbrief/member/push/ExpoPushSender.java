package kr.suhsaechan.secbrief.member.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.suhsaechan.secbrief.common.text.TextTruncation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Expo 푸시 발송기: 메시지 100건씩 발송 > 티켓, 티켓 1000건씩 접수증 조회
 * 요청 실패(연결·5xx·형식 오류)는 그 묶음 전체 FAILED, 다른 Expo 프로젝트의 토큰은 무효로 돌려줌
 */
@Slf4j
@Component
public class ExpoPushSender implements PushSender {

    // Expo 제한: 요청 1번에 메시지 100건, 접수증 1000건
    static final int MAXIMUM_MESSAGES_PER_REQUEST = 100;
    static final int MAXIMUM_RECEIPTS_PER_REQUEST = 1000;

    // 로그용 코드 최대 길이
    private static final int MAXIMUM_LOGGED_CODE_LENGTH = 40;
    private static final String SEND_PATH = "/--/api/v2/push/send";
    private static final String RECEIPTS_PATH = "/--/api/v2/push/getReceipts";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    // 티켓·접수증의 details.error: 더는 받지 않는 토큰
    private static final String ERROR_DEVICE_NOT_REGISTERED = "DeviceNotRegistered";
    // 요청 오류 code: 한 요청에 여러 Expo 프로젝트의 토큰이 섞임 (details: 프로젝트 > 토큰 목록)
    private static final String ERROR_TOO_MANY_EXPERIENCE_IDS = "PUSH_TOO_MANY_EXPERIENCE_IDS";
    private static final String STATUS_OK = "ok";
    private static final String DEFAULT_SOUND = "default";
    // 긴급 소식이라 기기가 절전 중이어도 바로 전달
    private static final String HIGH_PRIORITY = "high";

    private final boolean isEnabled;
    // 우리 앱의 Expo 프로젝트 이름 (@계정/slug, 비어 있으면 토큰이 가장 많은 프로젝트를 우리 것으로 봄)
    private final String ownExperienceId;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ExpoPushSender(@Value("${secbrief.push.enabled:true}") boolean isEnabled,
                          @Value("${secbrief.push.expo.base-url:https://exp.host}") String baseUrl,
                          @Value("${secbrief.push.expo.access-token:}") String accessToken,
                          @Value("${secbrief.push.expo.experience-id:}") String ownExperienceId,
                          ObjectMapper objectMapper) {
        this.isEnabled = isEnabled;
        this.ownExperienceId = ownExperienceId == null ? "" : ownExperienceId.strip();
        this.objectMapper = objectMapper;

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        // Expo 대시보드에서 발송 보안을 켠 경우에만 필요 (값은 로그에 남기지 않음)
        if (accessToken != null && !accessToken.isBlank()) {
            restClientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.strip());
        }
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean isAvailable() {
        return isEnabled;
    }

    @Override
    public List<PushSendOutcome> send(List<PushMessage> pushMessages) {
        List<PushSendOutcome> outcomes = new ArrayList<>(pushMessages.size());
        for (int start = 0; start < pushMessages.size(); start += MAXIMUM_MESSAGES_PER_REQUEST) {
            int end = Math.min(start + MAXIMUM_MESSAGES_PER_REQUEST, pushMessages.size());
            outcomes.addAll(sendChunk(pushMessages.subList(start, end)));
        }
        return outcomes;
    }

    @Override
    public Map<String, PushReceipt> fetchReceipts(List<String> ticketIds) {
        Map<String, PushReceipt> receiptByTicketId = new HashMap<>();
        for (int start = 0; start < ticketIds.size(); start += MAXIMUM_RECEIPTS_PER_REQUEST) {
            int end = Math.min(start + MAXIMUM_RECEIPTS_PER_REQUEST, ticketIds.size());
            JsonNode root = post(RECEIPTS_PATH, Map.of("ids", ticketIds.subList(start, end)));
            if (root == null || !root.path("data").isObject()) {
                continue;
            }
            root.path("data").properties().forEach(receiptEntry ->
                    receiptByTicketId.put(receiptEntry.getKey(), toReceipt(receiptEntry.getValue())));
        }
        return receiptByTicketId;
    }

    // 묶음 1개 발송: 요청 > 프로젝트가 섞였으면 남의 토큰을 빼고 한 번 더 > 티켓을 메시지 순서대로 대응
    private List<PushSendOutcome> sendChunk(List<PushMessage> chunk) {
        JsonNode root = post(SEND_PATH, chunk.stream().map(ExpoPushSender::toRequestMessage).toList());
        if (root == null) {
            return toAllFailed(chunk);
        }
        if (root.path("errors").isArray() && !root.path("errors").isEmpty()) {
            return resendWithoutForeignTokens(chunk, toForeignProjectTokens(root.path("errors").get(0)));
        }

        // 메시지가 1건이면 Expo 가 배열 대신 티켓 객체 하나를 줄 수 있음
        JsonNode ticketData = root.path("data");
        List<JsonNode> tickets = new ArrayList<>();
        if (ticketData.isArray()) {
            ticketData.forEach(tickets::add);
        } else if (ticketData.isObject()) {
            tickets.add(ticketData);
        }
        if (tickets.size() != chunk.size()) {
            log.warn("[push send] failed 502 : ticket count mismatch : sent={}, tickets={}", chunk.size(), tickets.size());
            return toAllFailed(chunk);
        }

        List<PushSendOutcome> outcomes = new ArrayList<>(chunk.size());
        for (int index = 0; index < chunk.size(); index++) {
            outcomes.add(toOutcome(chunk.get(index).pushToken(), tickets.get(index)));
        }
        return outcomes;
    }

    // 남의 프로젝트 토큰은 무효 처리, 나머지는 다시 발송 (남의 토큰을 못 가려냈으면 묶음 전체 FAILED)
    private List<PushSendOutcome> resendWithoutForeignTokens(List<PushMessage> chunk, Set<String> foreignTokens) {
        List<PushMessage> ownMessages = chunk.stream()
                .filter(pushMessage -> !foreignTokens.contains(pushMessage.pushToken()))
                .toList();
        if (foreignTokens.isEmpty() || ownMessages.size() == chunk.size()) {
            return toAllFailed(chunk);
        }
        log.warn("[push send] dropped foreign project tokens : count={}", chunk.size() - ownMessages.size());

        Map<String, PushSendOutcome> outcomeByToken = new HashMap<>();
        if (!ownMessages.isEmpty()) {
            sendChunk(ownMessages).forEach(outcome -> outcomeByToken.put(outcome.pushToken(), outcome));
        }
        return chunk.stream()
                .map(pushMessage -> outcomeByToken.getOrDefault(pushMessage.pushToken(),
                        PushSendOutcome.deviceNotRegistered(pushMessage.pushToken())))
                .toList();
    }

    // 요청 오류 1건 > 남의 프로젝트 토큰 (프로젝트 섞임 오류가 아니면 빈 집합, 오류 코드는 로그로)
    private Set<String> toForeignProjectTokens(JsonNode requestError) {
        String errorCode = requestError.path("code").asText("");
        JsonNode tokensByExperienceId = requestError.path("details");
        if (!ERROR_TOO_MANY_EXPERIENCE_IDS.equals(errorCode) || !tokensByExperienceId.isObject()) {
            log.warn("[push send] failed 502 : code={}", toLogSafeCode(errorCode));
            return Set.of();
        }

        // 우리 프로젝트: 설정값이 응답에 있으면 그것, 없으면 토큰이 가장 많은 프로젝트
        String ownExperienceIdInResponse = tokensByExperienceId.has(ownExperienceId) ? ownExperienceId : null;
        if (ownExperienceIdInResponse == null) {
            int largestTokenCount = -1;
            for (Map.Entry<String, JsonNode> experienceEntry : tokensByExperienceId.properties()) {
                if (experienceEntry.getValue().size() > largestTokenCount) {
                    largestTokenCount = experienceEntry.getValue().size();
                    ownExperienceIdInResponse = experienceEntry.getKey();
                }
            }
        }

        Set<String> foreignTokens = new HashSet<>();
        for (Map.Entry<String, JsonNode> experienceEntry : tokensByExperienceId.properties()) {
            if (!experienceEntry.getKey().equals(ownExperienceIdInResponse)) {
                experienceEntry.getValue().forEach(token -> foreignTokens.add(token.asText()));
            }
        }
        return foreignTokens;
    }

    // POST > 응답 JSON (연결 실패·읽기 실패·JSON 아님이면 null, 4xx·5xx 도 본문의 errors 를 읽으려고 그대로 해석)
    private JsonNode post(String path, Object requestBody) {
        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange((request, response) -> readJson(response.getBody().readAllBytes(),
                            response.getStatusCode().value()));
        } catch (RestClientException exception) {
            log.warn("[push send] failed 502 : unreachable : {}", exception.getMostSpecificCause().getClass().getSimpleName());
            return null;
        }
    }

    private JsonNode readJson(byte[] responseBytes, int statusCode) {
        try {
            JsonNode root = objectMapper.readTree(responseBytes);
            if (root == null || !root.isObject()) {
                log.warn("[push send] failed 502 : response is not a json object : status={}", statusCode);
                return null;
            }
            return root;
        } catch (IOException exception) {
            log.warn("[push send] failed 502 : response is not json : status={}", statusCode);
            return null;
        }
    }

    private static Map<String, Object> toRequestMessage(PushMessage pushMessage) {
        Map<String, Object> requestMessage = new LinkedHashMap<>();
        requestMessage.put("to", pushMessage.pushToken());
        requestMessage.put("title", pushMessage.title());
        requestMessage.put("body", pushMessage.body());
        requestMessage.put("data", pushMessage.data());
        requestMessage.put("channelId", pushMessage.channelId());
        requestMessage.put("sound", DEFAULT_SOUND);
        requestMessage.put("priority", HIGH_PRIORITY);
        return requestMessage;
    }

    private static PushSendOutcome toOutcome(String pushToken, JsonNode ticket) {
        if (STATUS_OK.equals(ticket.path("status").asText()) && ticket.path("id").isTextual()) {
            return PushSendOutcome.accepted(pushToken, ticket.path("id").asText());
        }
        return ERROR_DEVICE_NOT_REGISTERED.equals(ticket.path("details").path("error").asText())
                ? PushSendOutcome.deviceNotRegistered(pushToken)
                : PushSendOutcome.failed(pushToken);
    }

    private static PushReceipt toReceipt(JsonNode receipt) {
        if (STATUS_OK.equals(receipt.path("status").asText())) {
            return new PushReceipt(PushSendStatus.ACCEPTED, null);
        }
        String errorCode = receipt.path("details").path("error").asText("");
        return new PushReceipt(ERROR_DEVICE_NOT_REGISTERED.equals(errorCode)
                ? PushSendStatus.DEVICE_NOT_REGISTERED
                : PushSendStatus.FAILED, toLogSafeCode(errorCode));
    }

    private static List<PushSendOutcome> toAllFailed(List<PushMessage> chunk) {
        return chunk.stream().map(pushMessage -> PushSendOutcome.failed(pushMessage.pushToken())).toList();
    }

    // 로그용 코드 정리 (영문·숫자·밑줄만, 40자 제한)
    private static String toLogSafeCode(String errorCode) {
        String sanitizedCode = errorCode.replaceAll("[^A-Za-z0-9_]", "?");
        return TextTruncation.truncateKeepingSurrogatePairs(sanitizedCode, MAXIMUM_LOGGED_CODE_LENGTH);
    }
}
