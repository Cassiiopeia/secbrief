package kr.suhsaechan.secbrief.advisory.ai;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiTokenUsage;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKey;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKeyCall;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKeyCallRunner;
import kr.suhsaechan.secbrief.advisory.constant.AiProviderCode;
import kr.suhsaechan.secbrief.advisory.constant.KeyedAiProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Anthropic 제공자: 요청 1회 > 키 우선순위대로 Messages API(구조화 출력 JSON 스키마) > 응답 JSON 문자열
 * 키 순회·키마다 호출 기록 1행은 AiApiKeyCallRunner, 쓸 키가 없으면 호출하지 않음 (서버 기동·수집에 영향 없음)
 */
@Component
@RequiredArgsConstructor
public class AnthropicStructuredCompletionClient implements StructuredCompletionClient {

    public static final String PROVIDER_CODE = AiProviderCode.ANTHROPIC;
    private static final KeyedAiProvider PROVIDER = KeyedAiProvider.ANTHROPIC;

    private final AiApiKeyCallRunner aiApiKeyCallRunner;
    private final AnthropicClientCache anthropicClientCache;
    private final AiProviderModelService aiProviderModelService;

    @Override
    public String getProviderCode() {
        return PROVIDER_CODE;
    }

    @Override
    public boolean isAvailable() {
        return aiApiKeyCallRunner.hasCallableKey(PROVIDER);
    }

    /**
     * 요청 > 키 순회 > 거부 확인 > 텍스트(JSON). SDK 오류는 AI_API_ERROR (실패 종류만 메시지에 넣음)
     */
    @Override
    public StructuredCompletion requestCompletion(StructuredCompletionRequest request) {
        List<AiApiKey> callableKeys = aiApiKeyCallRunner.findCallableKeys(PROVIDER);
        anthropicClientCache.retainOnly(callableKeys);
        String model = aiProviderModelService.findModel(PROVIDER);
        MessageCreateParams params = toMessageCreateParams(request, model);
        Message message = aiApiKeyCallRunner.call(callableKeys, new AiApiKeyCall<>(PROVIDER, model, request.callType(),
                key -> anthropicClientCache.findClient(key).messages().create(params),
                AnthropicStructuredCompletionClient::toTokenUsage, AnthropicFailures::classify));

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

    private static MessageCreateParams toMessageCreateParams(StructuredCompletionRequest request, String model) {
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
