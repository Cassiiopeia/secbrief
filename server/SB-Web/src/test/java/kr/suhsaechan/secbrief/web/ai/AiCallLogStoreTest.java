package kr.suhsaechan.secbrief.web.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallAttempt;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallLog;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallLogQueryService;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallLogService;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallSummary;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallType;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiTokenUsage;
import kr.suhsaechan.secbrief.application.ai.AiDailyBudgetGuard;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigCache;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI 호출 기록 (DB): 호출마다 1행(토큰·설정 단가로 추정 비용), 호출부 트랜잭션이 되돌려져도 남음, 합계·필터 조회, 하루 예산 확인
 * 기록은 테스트 앞뒤로 비움 (다른 테스트가 같은 DB 를 씀)
 */
@SpringBootTest
@ActiveProfiles("test")
class AiCallLogStoreTest extends PostgresContainerTestSupport {

    private static final String ACTOR = "ai-call-log-test";
    private static final AiCallAttempt TITLE_ATTEMPT =
            new AiCallAttempt(AiCallType.TITLE_ANALYSIS, "anthropic", "claude-haiku-5-5", "운영", 842);
    private static final AiCallAttempt SUMMARY_ATTEMPT =
            new AiCallAttempt(AiCallType.SUMMARY, "anthropic", "claude-haiku-5-5", "예비", 1_530);
    private static final AiCallAttempt OLLAMA_ATTEMPT =
            new AiCallAttempt(AiCallType.SUMMARY, "ollama", "test-model", null, 5_120);

    @Autowired
    private AiCallLogService aiCallLogService;
    @Autowired
    private AiCallLogQueryService aiCallLogQueryService;
    @Autowired
    private AiDailyBudgetGuard aiDailyBudgetGuard;
    @Autowired
    private SystemConfigService systemConfigService;
    @Autowired
    private SystemConfigCache systemConfigCache;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearCallLogsAndConfigs() {
        jdbcTemplate.update("delete from ai_call_log");
        jdbcTemplate.update("delete from system_config");
        jdbcTemplate.update("delete from system_config_history");
        systemConfigCache.invalidate();
    }

    @Test
    void savesOneRowPerCallWithTokensAndEstimatedCost() {
        aiCallLogService.recordSuccess(TITLE_ATTEMPT, new AiTokenUsage(12_000, 3_000));

        Map<String, Object> row = jdbcTemplate.queryForMap("select * from ai_call_log");
        assertThat(row.get("call_type")).isEqualTo("TITLE_ANALYSIS");
        assertThat(row.get("provider")).isEqualTo("anthropic");
        assertThat(row.get("model")).isEqualTo("claude-haiku-5-5");
        assertThat(row.get("key_label")).isEqualTo("운영");
        assertThat(row.get("is_success")).isEqualTo(true);
        assertThat(row.get("failure_reason")).isNull();
        assertThat(row.get("latency_ms")).isEqualTo(842);
        assertThat(row.get("input_tokens")).isEqualTo(12_000);
        assertThat(row.get("output_tokens")).isEqualTo(3_000);
        // 기본 단가: 입력 0.10, 출력 0.50 (USD / 100만 토큰)
        assertThat((BigDecimal) row.get("estimated_cost_usd")).isEqualByComparingTo("0.002700");
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void estimatesCostWithPricesChangedInConfigStore() {
        systemConfigService.update(ConfigKey.PRICE_ANTHROPIC_INPUT_PER_1M, "1", ACTOR, null);
        systemConfigService.update(ConfigKey.PRICE_ANTHROPIC_OUTPUT_PER_1M, "5", ACTOR, null);

        aiCallLogService.recordSuccess(TITLE_ATTEMPT, new AiTokenUsage(12_000, 3_000));

        assertThat(jdbcTemplate.queryForObject("select estimated_cost_usd from ai_call_log", BigDecimal.class))
                .isEqualByComparingTo("0.027000");
    }

    @Test
    void keepsRecordEvenWhenCallerTransactionRollsBack() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transactionStatus -> {
            aiCallLogService.recordFailure(TITLE_ATTEMPT, AiFailureKind.RATE_LIMITED);
            transactionStatus.setRollbackOnly();
        });

        Map<String, Object> row = jdbcTemplate.queryForMap("select * from ai_call_log");
        assertThat(row.get("is_success")).isEqualTo(false);
        assertThat(row.get("failure_reason")).isEqualTo("RATE_LIMITED");
        assertThat(row.get("estimated_cost_usd")).isNull();
    }

    @Test
    void summarizesTodayAndThisMonth() {
        aiCallLogService.recordSuccess(TITLE_ATTEMPT, new AiTokenUsage(12_000, 3_000));
        aiCallLogService.recordSuccess(SUMMARY_ATTEMPT, new AiTokenUsage(2_000, 1_000));
        aiCallLogService.recordSuccess(OLLAMA_ATTEMPT, new AiTokenUsage(812, 96));
        aiCallLogService.recordFailure(SUMMARY_ATTEMPT, AiFailureKind.UNAUTHORIZED);
        // 지난달 기록은 합계에서 빠짐
        jdbcTemplate.update("insert into ai_call_log (call_type, provider, is_success, input_tokens, estimated_cost_usd, created_at) "
                + "values ('SUMMARY', 'anthropic', true, 999999, 9.000000, now() - interval '40 days')");

        for (AiCallSummary summary : List.of(aiCallLogQueryService.summarizeToday(), aiCallLogQueryService.summarizeThisMonth())) {
            assertThat(summary.totalCallCount()).isEqualTo(4);
            assertThat(summary.totalSuccessCount()).isEqualTo(3);
            assertThat(summary.totalFailureCount()).isEqualTo(1);
            assertThat(summary.totalInputTokens()).isEqualTo(14_812);
            assertThat(summary.totalOutputTokens()).isEqualTo(4_096);
            // 0.0027 + 0.0007 (Ollama 는 단가가 없어 합계에 더하지 않음)
            assertThat(summary.totalEstimatedCostUsd()).isEqualByComparingTo("0.003400");
        }
    }

    @Test
    void summaryOfEmptyLogIsZero() {
        AiCallSummary summary = aiCallLogQueryService.summarizeToday();

        assertThat(summary.totalCallCount()).isZero();
        assertThat(summary.totalFailureCount()).isZero();
        assertThat(summary.totalInputTokens()).isZero();
        assertThat(summary.totalEstimatedCostUsd()).isEqualByComparingTo("0");
    }

    @Test
    void findsRecentCallsNewestFirstWithFilters() {
        aiCallLogService.recordSuccess(TITLE_ATTEMPT, new AiTokenUsage(10, 5));
        aiCallLogService.recordFailure(SUMMARY_ATTEMPT, AiFailureKind.UNAUTHORIZED);
        aiCallLogService.recordSuccess(SUMMARY_ATTEMPT, new AiTokenUsage(20, 8));

        assertThat(aiCallLogQueryService.findRecentCalls(null, null, 0).getContent())
                .extracting(AiCallLog::getKeyLabel).containsExactly("예비", "예비", "운영");
        assertThat(aiCallLogQueryService.findRecentCalls(AiCallType.SUMMARY, null, 0).getContent()).hasSize(2);
        assertThat(aiCallLogQueryService.findRecentCalls(null, false, 0).getContent())
                .singleElement().satisfies(callLog -> assertThat(callLog.getFailureReason()).isEqualTo("UNAUTHORIZED"));
        assertThat(aiCallLogQueryService.findRecentCalls(AiCallType.SUMMARY, true, 0).getContent())
                .singleElement().satisfies(callLog -> assertThat(callLog.getInputTokens()).isEqualTo(20));
        assertThat(aiCallLogQueryService.findRecentCalls(AiCallType.TITLE_ANALYSIS, false, 0).getContent()).isEmpty();
        assertThat(aiCallLogQueryService.findRecentCalls(null, null, 5).getContent()).isEmpty();
    }

    @Test
    void dailyBudgetStopsAiWhenTodayCostReachesLimit() {
        aiCallLogService.recordSuccess(TITLE_ATTEMPT, new AiTokenUsage(12_000, 3_000));

        // 기본값 -1: 확인하지 않음
        assertThat(aiDailyBudgetGuard.isBudgetExhausted()).isFalse();
        systemConfigService.update(ConfigKey.AI_DAILY_BUDGET_USD, "0.01", ACTOR, null);
        assertThat(aiDailyBudgetGuard.isBudgetExhausted()).isFalse();
        // 오늘 추정 비용 0.0027 이 상한 0.0027 에 닿음
        systemConfigService.update(ConfigKey.AI_DAILY_BUDGET_USD, "0.0027", ACTOR, null);
        assertThat(aiDailyBudgetGuard.isBudgetExhausted()).isTrue();
    }
}
