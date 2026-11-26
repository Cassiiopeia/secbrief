package kr.suhsaechan.secbrief.advisory.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * HTTP 로 부르는 제공자 공통 (OpenAI·Google): JSON POST 1회 > 응답 JSON, 예외 > 실패 종류, 토큰 수 읽기
 * 재시도 없음 (실패하면 다음 키·다음 제공자·다음 틱이 이어받음), 주소·키·본문은 로그·예외에 넣지 않음
 */
@Component
public class ProviderHttpClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    // 응답이 이 시간 동안 멈추면 끊음 (Anthropic 요청 제한과 같은 60초)
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private final RestClient restClient;

    public ProviderHttpClient() {
        // 읽기 제한은 본문을 받는 중에도 적용 (멈춘 응답이 스케줄러 스레드를 붙잡지 않게)
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * JSON POST 1회: 2xx 면 응답 JSON (본문이 없으면 빈 노드), 그 밖은 RestClient 예외 그대로 (호출부가 classify 로 판정)
     */
    public JsonNode postJson(String url, Consumer<HttpHeaders> addRequestHeaders, Map<String, Object> requestBody) {
        JsonNode responseBody = restClient.post()
                .uri(URI.create(url))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(addRequestHeaders)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);
        return responseBody == null ? MissingNode.getInstance() : responseBody;
    }

    /** 예외 > 실패 종류: 상태 코드 응답, 연결 실패·시간 초과·JSON 아님, 주소 형식 오류(설정 문제) */
    public static AiFailureKind classify(RuntimeException failure) {
        if (failure instanceof RestClientResponseException responseException) {
            return AiFailureKind.fromStatusCode(responseException.getStatusCode().value(), null);
        }
        if (failure instanceof RestClientException) {
            return AiFailureKind.CONNECTION_FAILED;
        }
        return failure instanceof IllegalArgumentException ? AiFailureKind.INVALID_CONFIGURATION : AiFailureKind.UNKNOWN;
    }

    /** 응답의 토큰 수 필드 > 토큰 수 (없거나 정수가 아니면 null: 모름) */
    public static Integer toTokenCount(JsonNode tokenCountNode) {
        return tokenCountNode.isIntegralNumber() ? tokenCountNode.asInt() : null;
    }

    /** base-url 끝의 / 제거 후 경로를 붙임 */
    public static String toEndpointUrl(String baseUrl, String path) {
        return (baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "")) + path;
    }
}
