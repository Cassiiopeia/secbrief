package kr.suhsaechan.secbrief.advisory.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.ai.ProviderHttpClient;
import kr.suhsaechan.secbrief.advisory.entity.ArticleEmbedding;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Ollama 임베딩 제공자: 문장 묶음 1회 > POST {base-url}/api/embed ({model, input}) > embeddings (문장마다 벡터 1개)
 * 사용 조건 3개: 켜짐(secbrief.embedding.enabled), 주소(secbrief.ai.ollama.base-url 재사용), 임베딩 모델 — 하나라도 없으면 호출하지 않음
 */
@Component
public class OllamaTextEmbeddingProvider implements TextEmbeddingProvider {

    private static final String EMBED_PATH = "/api/embed";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    // 묶음 1회 응답 제한 (임베딩 틱 5분 안쪽, 검색 질의는 호출부가 더 짧게 끊음)
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final boolean isEnabled;
    private final String embedUrl;
    private final String model;
    private final String apiKey;

    public OllamaTextEmbeddingProvider(@Value("${secbrief.embedding.enabled:true}") boolean isEnabled,
                                       @Value("${secbrief.ai.ollama.base-url:}") String baseUrl,
                                       @Value("${secbrief.embedding.ollama.model:}") String model,
                                       @Value("${secbrief.ai.ollama.api-key:}") String apiKey) {
        // 읽기 제한은 본문을 받는 중에도 적용 (멈춘 응답이 스케줄러 스레드를 붙잡지 않게)
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.isEnabled = isEnabled;
        this.embedUrl = ProviderHttpClient.toEndpointUrlOrEmpty(baseUrl, EMBED_PATH);
        this.model = model == null ? "" : model.strip();
        this.apiKey = apiKey == null ? "" : apiKey.strip();
    }

    @Override
    public boolean isAvailable() {
        // 모델 이름이 저장 열보다 길면 기록할 수 없어 미설정으로 봄
        return isEnabled && !embedUrl.isEmpty() && !model.isEmpty()
                && model.length() <= ArticleEmbedding.MAXIMUM_EMBEDDING_MODEL_LENGTH;
    }

    @Override
    public String getModelName() {
        return model;
    }

    /**
     * 요청 > 2xx 확인 > 벡터 목록. 연결 실패·시간 초과·2xx 아님·벡터 수 불일치·빈 벡터는 AI_API_ERROR (주소·키·문장은 메시지에 넣지 않음)
     */
    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        if (!isAvailable()) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "embedding provider not configured");
        }
        return toEmbeddings(requestEmbed(texts), texts.size());
    }

    private JsonNode requestEmbed(List<String> texts) {
        try {
            return restClient.post()
                    .uri(URI.create(embedUrl))
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(this::addAuthorization)
                    .body(toEmbedRequestBody(texts))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (IllegalArgumentException exception) {
            // 주소 형식 오류 (설정 문제): 주소는 남기지 않음
            throw new CustomException(ErrorCode.AI_API_ERROR, "invalid base url");
        } catch (RestClientResponseException exception) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "status=" + exception.getStatusCode().value());
        } catch (RestClientException exception) {
            // 연결 실패·시간 초과·JSON 아님: 원인 종류만 남김
            throw new CustomException(ErrorCode.AI_API_ERROR,
                    "unreachable : " + exception.getMostSpecificCause().getClass().getSimpleName());
        }
    }

    // 인증 헤더: 키가 있을 때만 (인증 프록시 뒤의 Ollama)
    private void addAuthorization(HttpHeaders headers) {
        if (!apiKey.isEmpty()) {
            headers.setBearerAuth(apiKey);
        }
    }

    // 본문: {model, input: [문장, ...]}
    private Map<String, Object> toEmbedRequestBody(List<String> texts) {
        Map<String, Object> embedRequestBody = new LinkedHashMap<>();
        embedRequestBody.put("model", model);
        embedRequestBody.put("input", texts);
        return embedRequestBody;
    }

    // 응답 {embeddings: [[...], ...]} > 벡터 목록 (문장 수와 다르거나 빈 벡터·숫자 아님이 섞이면 통째로 실패)
    private static List<float[]> toEmbeddings(JsonNode responseBody, int textCount) {
        JsonNode embeddingsNode = responseBody == null ? null : responseBody.path("embeddings");
        if (embeddingsNode == null || !embeddingsNode.isArray() || embeddingsNode.size() != textCount) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "embedding count mismatch");
        }
        List<float[]> embeddings = new ArrayList<>();
        for (JsonNode embeddingNode : embeddingsNode) {
            embeddings.add(toEmbedding(embeddingNode));
        }
        return embeddings;
    }

    private static float[] toEmbedding(JsonNode embeddingNode) {
        if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "embedding is empty");
        }
        float[] embedding = new float[embeddingNode.size()];
        for (int dimensionIndex = 0; dimensionIndex < embedding.length; dimensionIndex++) {
            JsonNode valueNode = embeddingNode.get(dimensionIndex);
            if (!valueNode.isNumber()) {
                throw new CustomException(ErrorCode.AI_API_ERROR, "embedding is not numeric");
            }
            embedding[dimensionIndex] = valueNode.floatValue();
        }
        return embedding;
    }
}
