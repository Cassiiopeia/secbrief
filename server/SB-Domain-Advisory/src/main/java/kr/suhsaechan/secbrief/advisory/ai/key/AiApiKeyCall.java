package kr.suhsaechan.secbrief.advisory.ai.key;

import java.util.function.Function;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallType;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiTokenUsage;
import kr.suhsaechan.secbrief.advisory.constant.KeyedAiProvider;

/**
 * 키로 부르는 호출 1회분: 제공자, 모델, 용도(호출 기록용), 키 1개로 부르는 함수, 응답 > 토큰 사용량, 예외 > 실패 종류
 */
public record AiApiKeyCall<T>(KeyedAiProvider provider, String model, AiCallType callType,
                              Function<AiApiKey, T> callWithKey, Function<T, AiTokenUsage> readTokenUsage,
                              Function<RuntimeException, AiFailureKind> classifyFailure) {
}
