package kr.suhsaechan.secbrief.advisory.ai.calllog;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 추정 비용 계산: (입력 토큰 × 입력 단가 + 출력 토큰 × 출력 단가) / 100만 > USD (소수 6자리)
 */
public final class AiCostCalculator {

    private static final BigDecimal TOKENS_PER_PRICE_UNIT = BigDecimal.valueOf(1_000_000);
    // ai_call_log.estimated_cost_usd 소수 자리
    private static final int COST_SCALE = 6;

    private AiCostCalculator() {
    }

    /** 사용량을 모르면 null (0 으로 적지 않음: 집계에서 "모름"과 "무료"를 구분) */
    public static BigDecimal estimate(AiTokenUsage tokenUsage, BigDecimal inputPricePerMillionTokens,
                                      BigDecimal outputPricePerMillionTokens) {
        if (tokenUsage == null || !tokenUsage.isKnown()) {
            return null;
        }
        BigDecimal inputCost = toCost(tokenUsage.inputTokens(), inputPricePerMillionTokens);
        BigDecimal outputCost = toCost(tokenUsage.outputTokens(), outputPricePerMillionTokens);
        return inputCost.add(outputCost).divide(TOKENS_PER_PRICE_UNIT, COST_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal toCost(Integer tokenCount, BigDecimal pricePerMillionTokens) {
        return tokenCount == null ? BigDecimal.ZERO : pricePerMillionTokens.multiply(BigDecimal.valueOf(tokenCount));
    }
}
