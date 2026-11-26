package kr.suhsaechan.secbrief.web.admin.support;

import java.math.BigDecimal;
import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallLog;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;

/**
 * AI 호출 표 1줄: 시각, 용도 이름, 제공자, 모델, 키 별명, 성공 여부, 실패 종류 이름, 걸린 시간, 토큰, 추정 비용
 */
public record AdminAiCallRow(Instant createdAt, String callTypeLabel, String provider, String model, String keyLabel,
                             boolean isSuccess, String failureLabel, Integer latencyMilliseconds, Integer inputTokens,
                             Integer outputTokens, BigDecimal estimatedCostUsd) {

    public static AdminAiCallRow of(AiCallLog callLog) {
        return new AdminAiCallRow(callLog.getCreatedAt(), callLog.getCallType().getLabel(), callLog.getProvider(),
                callLog.getModel(), callLog.getKeyLabel(), callLog.isSuccess(),
                toFailureLabel(callLog.getFailureReason()), callLog.getLatencyMilliseconds(), callLog.getInputTokens(),
                callLog.getOutputTokens(), callLog.getEstimatedCostUsd());
    }

    // 실패 종류 코드 > 화면 이름 (이 버전이 모르는 코드는 코드 그대로)
    private static String toFailureLabel(String failureReason) {
        if (failureReason == null) {
            return null;
        }
        try {
            return AiFailureKind.valueOf(failureReason).getLabel();
        } catch (IllegalArgumentException exception) {
            return failureReason;
        }
    }
}
