package kr.suhsaechan.secbrief.advisory.ai;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallAttempt;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallLogService;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallType;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiTokenUsage;
import kr.suhsaechan.secbrief.advisory.ai.key.AnthropicApiKey;
import kr.suhsaechan.secbrief.advisory.ai.key.AnthropicApiKeyService;
import kr.suhsaechan.secbrief.advisory.ai.key.ApiKeyFallbackExecutor;
import kr.suhsaechan.secbrief.advisory.constant.AiProviderCode;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Anthropic 제공자: 요청 1회 > 키 우선순위대로 Messages API(구조화 출력 JSON 스키마) > 응답 JSON 문자열, 키마다 호출 기록 1행
 * 쓸 키가 없으면 호출하지 않음 (서버 기동·수집에 영향 없음), 키 값·입력 글은 로그·예외·기록에 넣지 않음
 */
@Component
public class AnthropicStructuredCompletionClient implements StructuredCompletionClient {

    public static final String PROVIDER_CODE = AiProviderCode.ANTHROPIC;

    private final AnthropicApiKeyService anthropicApiKeyService;
    private final AnthropicClientCache anthropicClientCache;
    private final ApiKeyFallbackExecutor apiKeyFallbackExecutor;
    private final AiCallLogService aiCallLogService;
    private final String model;

    public AnthropicStructuredCompletionClient(AnthropicApiKeyService anthropicApiKeyService,
                                               AnthropicClientCache anthropicClientCache,
                                               ApiKeyFallbackExecutor apiKeyFallbackExecutor,
                                               AiCallLogService aiCallLogService,
                                               @Value("${secbrief.ai.model:claude-haiku-5-5}") String model) {
        this.anthropicApiKeyService = anthropicApiKeyService;
        this.anthropicClientCache = anthropicClientCache;
        this.apiKeyFallbackExecutor = apiKeyFallbackExecutor;
        this.aiCallLogService = aiCallLogService;
        this.model = model;
    }

    @Override
    public String getProviderCode() {
        return PROVIDER_CODE;
    }

    @Override
    public boolean isAvailable() {
        return anthropicApiKeyService.hasCallableKey();
    }

    /**
     * 요청 > 키 순회 > 거부 확인 > 텍스트(JSON). SDK 오류는 AI_API_ERROR (실패 종류만 메시지에 넣음)
     */
    @Override
    public StructuredCompletion requestCompletion(StructuredCompletionRequest request) {
        List<AnthropicApiKey> callableKeys = anthropicApiKeyService.findCallableKeys();
        if (callableKeys.isEmpty()) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "provider not configured");
        }
        anthropicClientCache.retainOnly(callableKeys);
        MessageCreateParams params = toMessageCreateParams(request);
        Message message = apiKeyFallbackExecutor.execute(callableKeys,
                key -> requestMessage(key, params, request.callType()), AnthropicFailures::classify);

        AiTokenUsage tokenUsage = toTokenUsage(message);
        if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            return StructuredCompletion.refused(tokenUsage);
        }
        String responseJson = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
        return StructuredCompletion.of(responseJson,
                message.stopReason().filter(StopReason.MAX_TOKENS::equals).isPresent(), tokenUsage);
    }

    // 키 1개로 호출 1회 + 호출 기록 (성공·실패 공통), 예외는 그대로 던져 키 순회가 판정
    private Message requestMessage(AnthropicApiKey key, MessageCreateParams params, AiCallType callType) {
        long startedAtNanoseconds = System.nanoTime();
        try {
            Message message = anthropicClientCache.findClient(key).messages().create(params);
            aiCallLogService.recordSuccess(toAttempt(key, callType, startedAtNanoseconds), toTokenUsage(message));
            return message;
        } catch (RuntimeException exception) {
            aiCallLogService.recordFailure(toAttempt(key, callType, startedAtNanoseconds),
                    AnthropicFailures.classify(exception));
            throw exception;
        }
    }

    private AiCallAttempt toAttempt(AnthropicApiKey key, AiCallType callType, long startedAtNanoseconds) {
        return new AiCallAttempt(callType, PROVIDER_CODE, model, key.nickname(),
                Duration.ofNanos(System.nanoTime() - startedAtNanoseconds).toMillis());
    }

    private MessageCreateParams toMessageCreateParams(StructuredCompletionRequest request) {
        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(request.maximumOutputTokens())
                // 분류·번역·짧은 요약이라 생각 끄고 낮은 노력 (Haiku 5.5: 노력 high 이하에서만 끌 수 있음)
                .thinking(ThinkingConfigDisabled.builder().build())
                .system(request.systemInstruction())
                .outputConfig(toOutputConfig(request.outputJsonSchema()))
                .addUserMessage(request.userMessage())
                .build();
    }

    private static AiTokenUsage toTokenUsage(Message message) {
        return new AiTokenUsage(toTokenCount(message.usage().inputTokens()), toTokenCount(message.usage().outputTokens()));
    }

    private static Integer toTokenCount(long tokenCount) {
        return (int) Math.min(tokenCount, Integer.MAX_VALUE);
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
