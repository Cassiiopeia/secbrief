package kr.suhsaechan.secbrief.advisory.ai.key;

import java.time.Duration;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallAttempt;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallLogService;
import kr.suhsaechan.secbrief.advisory.constant.KeyedAiProvider;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 키로 부르는 제공자 공통 호출 (Anthropic·OpenAI·Google): 쓸 키 조회 > 키 순회(쿨다운·다음 키) > 키마다 호출 기록 1행
 * 쓸 키가 없으면 호출하지 않고 AI_API_ERROR, 키 값은 로그·예외·기록에 넣지 않음 (기록에는 별명만)
 */
@Component
@RequiredArgsConstructor
public class AiApiKeyCallRunner {

    private final AiApiKeyServiceRegistry aiApiKeyServiceRegistry;
    private final ApiKeyFallbackExecutor apiKeyFallbackExecutor;
    private final AiCallLogService aiCallLogService;

    public boolean hasCallableKey(KeyedAiProvider provider) {
        return aiApiKeyServiceRegistry.find(provider).hasCallableKey();
    }

    /** 호출에 쓸 키 (우선순위 순) */
    public List<AiApiKey> findCallableKeys(KeyedAiProvider provider) {
        return aiApiKeyServiceRegistry.find(provider).findCallableKeys();
    }

    /** 지금 쓸 키로 호출 */
    public <T> T call(AiApiKeyCall<T> keyCall) {
        return call(findCallableKeys(keyCall.provider()), keyCall);
    }

    /**
     * 주어진 키로 호출 (키 목록을 먼저 읽어 쓴 호출부용): 키 순회 > 성공한 응답, 다 실패하면 AI_API_ERROR
     */
    public <T> T call(List<AiApiKey> callableKeys, AiApiKeyCall<T> keyCall) {
        if (callableKeys.isEmpty()) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "provider not configured");
        }
        return apiKeyFallbackExecutor.execute(callableKeys, key -> callOnce(key, keyCall), keyCall.classifyFailure());
    }

    // 키 1개로 호출 1회 + 호출 기록 (성공·실패 공통), 예외는 그대로 던져 키 순회가 판정
    private <T> T callOnce(AiApiKey key, AiApiKeyCall<T> keyCall) {
        long startedAtNanoseconds = System.nanoTime();
        try {
            T response = keyCall.callWithKey().apply(key);
            aiCallLogService.recordSuccess(toAttempt(key, keyCall, startedAtNanoseconds),
                    keyCall.readTokenUsage().apply(response));
            return response;
        } catch (RuntimeException exception) {
            aiCallLogService.recordFailure(toAttempt(key, keyCall, startedAtNanoseconds),
                    keyCall.classifyFailure().apply(exception));
            throw exception;
        }
    }

    private static AiCallAttempt toAttempt(AiApiKey key, AiApiKeyCall<?> keyCall, long startedAtNanoseconds) {
        return new AiCallAttempt(keyCall.callType(), keyCall.provider().getProviderCode(), keyCall.model(),
                key.nickname(), Duration.ofNanos(System.nanoTime() - startedAtNanoseconds).toMillis());
    }
}
