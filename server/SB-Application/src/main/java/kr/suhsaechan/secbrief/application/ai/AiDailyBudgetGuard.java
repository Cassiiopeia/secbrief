package kr.suhsaechan.secbrief.application.ai;

import java.math.BigDecimal;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallLogQueryService;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 하루 AI 예산 확인: AI_DAILY_BUDGET_USD 가 0 이상이면 오늘(서울 날짜) 추정 비용 합이 상한에 닿았는지 (제목 분석·요약 공통)
 * 음수(-1)면 확인하지 않음, 집계에 실패하면 통과 (예산 확인 장애가 AI 처리를 멈추지 않게)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiDailyBudgetGuard {

    private final SystemConfigService systemConfigService;
    private final AiCallLogQueryService aiCallLogQueryService;

    /**
     * 오늘 예산을 다 썼는지 (true 면 호출부가 이번 틱을 건너뜀, 로그 1줄)
     */
    public boolean isBudgetExhausted() {
        try {
            BigDecimal dailyBudgetUsd = systemConfigService.getDecimal(ConfigKey.AI_DAILY_BUDGET_USD);
            if (dailyBudgetUsd.signum() < 0) {
                return false;
            }
            BigDecimal todayCostUsd = aiCallLogQueryService.summarizeToday().totalEstimatedCostUsd();
            if (todayCostUsd.compareTo(dailyBudgetUsd) < 0) {
                return false;
            }
            log.info("[ai budget] skipped : daily budget reached : budgetUsd={}, todayCostUsd={}",
                    dailyBudgetUsd.toPlainString(), todayCostUsd.toPlainString());
            return true;
        } catch (RuntimeException exception) {
            log.warn("[ai budget] check failed, passing : cause={}", exception.getClass().getSimpleName());
            return false;
        }
    }
}
