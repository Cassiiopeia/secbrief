package kr.suhsaechan.secbrief.advisory.ai.calllog;

import java.math.BigDecimal;

/**
 * 기간 합계 (집계 조회 결과): 호출 수, 성공 수, 입력·출력 토큰, 추정 비용 (기록이 없으면 합계 값은 null)
 */
public record AiCallSummary(Long callCount, Long successCount, Long inputTokens, Long outputTokens,
                            BigDecimal estimatedCostUsd) {

    public long totalCallCount() {
        return callCount == null ? 0 : callCount;
    }

    public long totalSuccessCount() {
        return successCount == null ? 0 : successCount;
    }

    public long totalFailureCount() {
        return totalCallCount() - totalSuccessCount();
    }

    public long totalInputTokens() {
        return inputTokens == null ? 0 : inputTokens;
    }

    public long totalOutputTokens() {
        return outputTokens == null ? 0 : outputTokens;
    }

    public BigDecimal totalEstimatedCostUsd() {
        return estimatedCostUsd == null ? BigDecimal.ZERO : estimatedCostUsd;
    }
}
