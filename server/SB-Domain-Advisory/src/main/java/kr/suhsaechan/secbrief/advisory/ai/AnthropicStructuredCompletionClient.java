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
 * Anthropic 제공자: 요청 1회 > Messages API(구조화 출력 JSON 스키마) > 응답 JSON 문자열
 * 키가 없으면 SDK 클라이언트를 만들지 않음 (서버 기동·수집에 영향 없음)
 */
@Component
public class AnthropicStructuredCompletionClient implements StructuredCompletionClient {

    public static final String PROVIDER_CODE = "anthropic";

    // 요청 1회 제한 시간 (스케줄러 스레드를 오래 잡지 않게)
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    // SDK 자동 재시도 (429·5xx), 넘으면 호출 실패 (호출부가 다음 틱에 다시 함)
    private static final int MAXIMUM_RETRY_COUNT = 1;

    private final AnthropicClient anthropicClient;
    private final String model;

    public AnthropicStructuredCompletionClient(@Value("${secbrief.ai.anthropic-api-key:}") String anthropicApiKey,
                                         @Value("${secbrief.ai.model:claude-haiku-5-5}") String model) {
        this.anthropicClient = anthropicApiKey == null || anthropicApiKey.isBlank()
                ? null
                : AnthropicOkHttpClient.builder()
                        .apiKey(anthropicApiKey)
                        .timeout(REQUEST_TIMEOUT)
                        .maxRetries(MAXIMUM_RETRY_COUNT)
                        .build();
        this.model = model;
    }

    @Override
    public String getProviderCode() {
        return PROVIDER_CODE;
    }

    @Override
    public boolean isAvailable() {
        return anthropicClient != null;
    }

    /**
     * 요청 > 거부 확인 > 텍스트(JSON). SDK 오류는 AI_API_ERROR (키·입력 글은 메시지에 넣지 않음)
     */
    @Override
    public StructuredCompletion requestCompletion(StructuredCompletionRequest request) {
        if (!isAvailable()) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "provider not configured");
        }
        Message message = requestMessage(request);
        if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            return StructuredCompletion.refused();
        }
        String responseJson = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
        return StructuredCompletion.of(responseJson,
                message.stopReason().filter(StopReason.MAX_TOKENS::equals).isPresent());
    }

    private Message requestMessage(StructuredCompletionRequest request) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(request.maximumOutputTokens())
                // 분류·번역·짧은 요약이라 생각 끄고 낮은 노력 (Haiku 5.5: 노력 high 이하에서만 끌 수 있음)
                .thinking(ThinkingConfigDisabled.builder().build())
                .system(request.systemInstruction())
                .outputConfig(toOutputConfig(request.outputJsonSchema()))
                .addUserMessage(request.userMessage())
                .build();
        try {
            return anthropicClient.messages().create(params);
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.AI_API_ERROR, exception.getClass().getSimpleName());
        }
    }

    // 구조화 출력: JSON 스키마 + 낮은 노력
    private static OutputConfig toOutputConfig(Map<String, Object> outputJsonSchema) {
        JsonOutputFormat.Schema.Builder schemaBuilder = JsonOutputFormat.Schema.builder();
        for (Map.Entry<String, Object> schemaEntry : outputJsonSchema.entrySet()) {
            schemaBuilder.putAdditionalProperty(schemaEntry.getKey(), JsonValue.from(schemaEntry.getValue()));
        }
        return OutputConfig.builder()
                .effort(OutputConfig.Effort.LOW)
                .format(JsonOutputFormat.builder().schema(schemaBuilder.build()).build())
                .build();
    }
}
