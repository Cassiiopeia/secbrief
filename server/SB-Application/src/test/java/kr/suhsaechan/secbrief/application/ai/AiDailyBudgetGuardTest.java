package kr.suhsaechan.secbrief.application.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallLogQueryService;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallSummary;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import org.junit.jupiter.api.Test;

/**
 * 하루 AI 예산: 음수면 확인하지 않음, 0 이상이면 오늘 추정 비용이 상한에 닿았을 때 소진, 집계 실패는 통과
 */
class AiDailyBudgetGuardTest {

    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final AiCallLogQueryService aiCallLogQueryService = mock(AiCallLogQueryService.class);
    private final AiDailyBudgetGuard guard = new AiDailyBudgetGuard(systemConfigService, aiCallLogQueryService);

    @Test
    void doesNotCheckCostWhenBudgetIsNegative() {
        givenBudget("-1");

        assertThat(guard.isBudgetExhausted()).isFalse();
        verifyNoInteractions(aiCallLogQueryService);
    }

    @Test
    void passesWhileTodayCostIsBelowBudget() {
        givenBudget("1.5");
        givenTodayCost("1.499999");

        assertThat(guard.isBudgetExhausted()).isFalse();
    }

    @Test
    void isExhaustedWhenTodayCostReachesBudget() {
        givenBudget("1.5");
        givenTodayCost("1.500000");

        assertThat(guard.isBudgetExhausted()).isTrue();
    }

    @Test
    void zeroBudgetStopsAiEvenWithoutAnyCall() {
        // 0: 오늘 기록이 없어도(합계 null) 상한에 닿은 것으로 봄 (AI 처리 멈춤 스위치)
        givenBudget("0");
        when(aiCallLogQueryService.summarizeToday()).thenReturn(new AiCallSummary(0L, null, null, null, null));

        assertThat(guard.isBudgetExhausted()).isTrue();
    }

    @Test
    void passesWhenCostQueryFails() {
        givenBudget("1.5");
        when(aiCallLogQueryService.summarizeToday()).thenThrow(new IllegalStateException("db"));

        assertThat(guard.isBudgetExhausted()).isFalse();
    }

    private void givenBudget(String dailyBudgetUsd) {
        when(systemConfigService.getDecimal(ConfigKey.AI_DAILY_BUDGET_USD)).thenReturn(new BigDecimal(dailyBudgetUsd));
    }

    private void givenTodayCost(String todayCostUsd) {
        when(aiCallLogQueryService.summarizeToday())
                .thenReturn(new AiCallSummary(10L, 10L, 1000L, 500L, new BigDecimal(todayCostUsd)));
    }
}
