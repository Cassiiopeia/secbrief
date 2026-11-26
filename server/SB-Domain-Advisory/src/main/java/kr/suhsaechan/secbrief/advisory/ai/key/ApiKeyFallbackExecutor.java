package kr.suhsaechan.secbrief.advisory.ai.key;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 키 순회 (제공자 SDK 와 무관): 우선순위 순으로 시도 > 키 문제면 다음 키 > 다 실패하면 AI_API_ERROR
 * 쿨다운 중인 키는 뒤로 미루되 전부 쿨다운이어도 끝까지 시도, 키를 바꿔도 안 고쳐지는 실패는 바로 던짐
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyFallbackExecutor {

    private final ApiKeyHealthTracker apiKeyHealthTracker;

    /**
     * enabledKeys: 켜진 키 (우선순위 순), callWithKey: 키 1개로 호출 1회, classifyFailure: 예외 > 실패 종류
     * 로그에는 키 id 만 남김 (별명은 관리자 입력값, 키 값은 남기지 않음)
     */
    public <T> T execute(List<AiApiKey> enabledKeys, Function<AiApiKey, T> callWithKey,
                         Function<RuntimeException, AiFailureKind> classifyFailure) {
        List<AiApiKey> keysInTryOrder = toTryOrder(enabledKeys);
        AiFailureKind lastFailureKind = null;
        for (int keyIndex = 0; keyIndex < keysInTryOrder.size(); keyIndex++) {
            AiApiKey key = keysInTryOrder.get(keyIndex);
            try {
                T callResult = callWithKey.apply(key);
                apiKeyHealthTracker.recordSuccess(key.id());
                return callResult;
            } catch (RuntimeException exception) {
                AiFailureKind failureKind = classifyFailure.apply(exception);
                if (!failureKind.shouldTryNextKey()) {
                    throw new CustomException(ErrorCode.AI_API_ERROR, "call failed : " + failureKind);
                }
                apiKeyHealthTracker.recordFailure(key.id(), failureKind);
                lastFailureKind = failureKind;
                boolean hasNextKey = keyIndex + 1 < keysInTryOrder.size();
                log.warn("[ai key] key failed, {} : keyId={}, kind={}",
                        hasNextKey ? "trying next key" : "no more keys", key.id(), failureKind);
            }
        }
        throw new CustomException(ErrorCode.AI_API_ERROR,
                lastFailureKind == null ? "no api key" : "all keys failed : last=" + lastFailureKind);
    }

    // 쿨다운이 아닌 키 먼저 (우선순위 유지), 쿨다운 중인 키는 뒤에
    private List<AiApiKey> toTryOrder(List<AiApiKey> enabledKeys) {
        List<AiApiKey> readyKeys = new ArrayList<>();
        List<AiApiKey> coolingKeys = new ArrayList<>();
        for (AiApiKey key : enabledKeys) {
            (apiKeyHealthTracker.isCoolingDown(key.id()) ? coolingKeys : readyKeys).add(key);
        }
        readyKeys.addAll(coolingKeys);
        return readyKeys;
    }
}
