package kr.suhsaechan.secbrief.application.admin.stats;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 사용 추이 지표: 호출 수(count), 추정 비용 USD(cost)
 */
@Getter
@RequiredArgsConstructor
public enum AdminAiUsageMetric {
    CALL_COUNT("count"),
    COST_USD("cost");

    private final String parameterValue;

    /**
     * 화면 값 > 지표 (cost 만 비용, 그 밖의 값·빈 값은 호출 수)
     */
    public static AdminAiUsageMetric fromParameter(String parameter) {
        return parameter != null && COST_USD.parameterValue.equalsIgnoreCase(parameter.trim()) ? COST_USD : CALL_COUNT;
    }
}
