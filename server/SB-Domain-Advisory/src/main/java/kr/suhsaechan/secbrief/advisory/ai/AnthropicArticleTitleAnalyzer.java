package kr.suhsaechan.secbrief.advisory.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Anthropic 분석기: 제목 1건 > Messages API(구조화 출력 JSON 스키마) > 분석 결과
 * 키가 없으면 SDK 클라이언트를 만들지 않음 (서버 기동·수집에 영향 없음)
 */
@Component
public class AnthropicArticleTitleAnalyzer implements ArticleTitleAnalyzer {

    // 출력은 JSON 한 줄 (주제 + 제목) 이라 짧게
    private static final long MAXIMUM_OUTPUT_TOKENS = 256L;
    // 요청 1건 제한 시간 (스케줄러 스레드를 오래 잡지 않게)
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    // SDK 자동 재시도 (429·5xx), 넘으면 이 기사의 시도 1회 실패로 기록
    private static final int MAXIMUM_RETRY_COUNT = 1;

    private final AnthropicClient anthropicClient;
    private final String model;
    private final OutputConfig outputConfig;

    public AnthropicArticleTitleAnalyzer(@Value("${secbrief.ai.anthropic-api-key:}") String anthropicApiKey,
                                         @Value("${secbrief.ai.model:claude-haiku-5-5}") String model) {
        this.anthropicClient = anthropicApiKey == null || anthropicApiKey.isBlank()
                ? null
                : AnthropicOkHttpClient.builder()
                        .apiKey(anthropicApiKey)
                        .timeout(REQUEST_TIMEOUT)
                        .maxRetries(MAXIMUM_RETRY_COUNT)
                        .build();
        this.model = model;
        this.outputConfig = toOutputConfig();
    }

    @Override
    public boolean isAvailable() {
        return anthropicClient != null;
    }

    /**
     * 요청 > 거부·빈 응답 확인 > 텍스트(JSON) > 분석 결과. SDK 오류는 AI_API_ERROR (키·제목은 메시지에 넣지 않음)
     */
    @Override
    public ArticleTitleAnalysis analyze(ArticleTitleAnalysisRequest request) {
        if (!isAvailable()) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "api key missing");
        }
        Message message = requestMessage(request);
        if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "refused");
        }
        String responseJson = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
        return ArticleTitleAnalysisPrompt.toAnalysis(responseJson);
    }

    private Message requestMessage(ArticleTitleAnalysisRequest request) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAXIMUM_OUTPUT_TOKENS)
                // 단순 분류라 생각 끄고 낮은 노력 (Haiku 5.5: 노력 high 이하에서만 끌 수 있음)
                .thinking(ThinkingConfigDisabled.builder().build())
                .system(ArticleTitleAnalysisPrompt.SYSTEM_INSTRUCTION)
                .outputConfig(outputConfig)
                .addUserMessage(ArticleTitleAnalysisPrompt.toUserMessage(request))
                .build();
        try {
            return anthropicClient.messages().create(params);
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.AI_API_ERROR, exception.getClass().getSimpleName());
        }
    }

    // 구조화 출력: JSON 스키마 + 낮은 노력
    private static OutputConfig toOutputConfig() {
        JsonOutputFormat.Schema.Builder schemaBuilder = JsonOutputFormat.Schema.builder();
        for (Map.Entry<String, Object> schemaEntry : ArticleTitleAnalysisPrompt.toOutputJsonSchema().entrySet()) {
            schemaBuilder.putAdditionalProperty(schemaEntry.getKey(), JsonValue.from(schemaEntry.getValue()));
        }
        return OutputConfig.builder()
                .effort(OutputConfig.Effort.LOW)
                .format(JsonOutputFormat.builder().schema(schemaBuilder.build()).build())
                .build();
    }
}
