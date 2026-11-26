package kr.suhsaechan.secbrief.advisory.ai.calllog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 추정 비용: 토큰 × 100만 토큰당 단가, 소수 6자리, 사용량을 모르면 null
 */
class AiCostCalculatorTest {

    private static final BigDecimal INPUT_PRICE = new BigDecimal("0.10");
    private static final BigDecimal OUTPUT_PRICE = new BigDecimal("0.50");

    @Test
    void multipliesTokensByPricePerMillion() {
        // 입력 12,000 × 0.10 / 100만 = 0.0012, 출력 3,000 × 0.50 / 100만 = 0.0015
        assertThat(AiCostCalculator.estimate(new AiTokenUsage(12_000, 3_000), INPUT_PRICE, OUTPUT_PRICE))
                .isEqualByComparingTo("0.002700");
    }

    @Test
    void roundsToSixDecimalPlaces() {
        // 입력 7 × 0.10 / 100만 = 0.0000007 > 0.000001
        BigDecimal cost = AiCostCalculator.estimate(new AiTokenUsage(7, 0), INPUT_PRICE, OUTPUT_PRICE);

        assertThat(cost.scale()).isEqualTo(6);
        assertThat(cost).isEqualByComparingTo("0.000001");
    }

    @Test
    void treatsMissingSideAsZeroAndUnknownUsageAsNull() {
        assertThat(AiCostCalculator.estimate(new AiTokenUsage(1_000_000, null), INPUT_PRICE, OUTPUT_PRICE))
                .isEqualByComparingTo("0.10");
        assertThat(AiCostCalculator.estimate(AiTokenUsage.UNKNOWN, INPUT_PRICE, OUTPUT_PRICE)).isNull();
        assertThat(AiCostCalculator.estimate(null, INPUT_PRICE, OUTPUT_PRICE)).isNull();
    }
}
