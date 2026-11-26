package kr.suhsaechan.secbrief.advisory.ai;

import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicRetryableException;
import com.anthropic.errors.AnthropicServiceException;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;

/**
 * Anthropic SDK 예외 > 실패 종류 (다음 키로 넘길지 판정용)
 * 상태 코드가 있는 예외(UnauthorizedException·RateLimitException·OverloadedException 등)는 모두 AnthropicServiceException 의 하위라 statusCode() 로 나눔
 */
final class AnthropicFailures {

    private AnthropicFailures() {
    }

    static AiFailureKind classify(RuntimeException failure) {
        if (failure instanceof AnthropicServiceException serviceException) {
            return AiFailureKind.fromStatusCode(serviceException.statusCode(), toErrorText(serviceException));
        }
        // 연결 실패·시간 초과 (AnthropicIoException), SDK 가 다시 해 볼 만하다고 본 실패
        if (failure instanceof AnthropicIoException || failure instanceof AnthropicRetryableException) {
            return AiFailureKind.CONNECTION_FAILED;
        }
        return AiFailureKind.UNKNOWN;
    }

    // 크레딧 부족 판정용 오류 본문 (어디에도 남기지 않음, 본문을 읽지 못하면 빈 문자열)
    private static String toErrorText(AnthropicServiceException serviceException) {
        try {
            return serviceException.apiMessage().orElse("") + " " + serviceException.body();
        } catch (RuntimeException exception) {
            return "";
        }
    }
}
