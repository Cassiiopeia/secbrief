package kr.suhsaechan.secbrief.advisory.ai.calllog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.support.MutableClock;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;

/**
 * 호출 기록 저장: 성공(토큰·설정 단가로 추정 비용)·실패(실패 종류) 1행, 새 트랜잭션, 저장·커밋 실패는 삼킴
 */
class AiCallLogServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-26T03:00:00Z");
    private static final AiCallAttempt ANTHROPIC_ATTEMPT =
            new AiCallAttempt(AiCallType.TITLE_ANALYSIS, "anthropic", "claude-haiku-5-5", "운영", 842);
    private static final AiCallAttempt OLLAMA_ATTEMPT =
            new AiCallAttempt(AiCallType.SUMMARY, "ollama", "test-model", null, 5_120);

    private final AiCallLogRepository aiCallLogRepository = mock(AiCallLogRepository.class);
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final AiCallLogService service = new AiCallLogService(
            aiCallLogRepository, systemConfigService, transactionManager, new MutableClock(NOW));

    @BeforeEach
    void givenConfiguredPrices() {
        when(systemConfigService.getDecimal(ConfigKey.PRICE_ANTHROPIC_INPUT_PER_1M)).thenReturn(new BigDecimal("0.10"));
        when(systemConfigService.getDecimal(ConfigKey.PRICE_ANTHROPIC_OUTPUT_PER_1M)).thenReturn(new BigDecimal("0.50"));
    }

    @Test
    void recordsSuccessWithTokensAndEstimatedCostFromConfiguredPrices() {
        service.recordSuccess(ANTHROPIC_ATTEMPT, new AiTokenUsage(12_000, 3_000));

        AiCallLog callLog = captureSavedCallLog();
        assertThat(callLog.getCallType()).isEqualTo(AiCallType.TITLE_ANALYSIS);
        assertThat(callLog.getProvider()).isEqualTo("anthropic");
        assertThat(callLog.getModel()).isEqualTo("claude-haiku-5-5");
        assertThat(callLog.getKeyLabel()).isEqualTo("운영");
        assertThat(callLog.isSuccess()).isTrue();
        assertThat(callLog.getFailureReason()).isNull();
        assertThat(callLog.getLatencyMilliseconds()).isEqualTo(842);
        assertThat(callLog.getInputTokens()).isEqualTo(12_000);
        assertThat(callLog.getOutputTokens()).isEqualTo(3_000);
        assertThat(callLog.getEstimatedCostUsd()).isEqualByComparingTo("0.002700");
        assertThat(callLog.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void recordsFailureWithShortReasonCodeAndNoCost() {
        service.recordFailure(ANTHROPIC_ATTEMPT, AiFailureKind.RATE_LIMITED);

        AiCallLog callLog = captureSavedCallLog();
        assertThat(callLog.isSuccess()).isFalse();
        assertThat(callLog.getFailureReason()).isEqualTo("RATE_LIMITED");
        assertThat(callLog.getKeyLabel()).isEqualTo("운영");
        assertThat(callLog.getInputTokens()).isNull();
        assertThat(callLog.getEstimatedCostUsd()).isNull();
    }

    @Test
    void doesNotEstimateCostForProviderWithoutPrice() {
        service.recordSuccess(OLLAMA_ATTEMPT, new AiTokenUsage(812, 96));

        AiCallLog callLog = captureSavedCallLog();
        assertThat(callLog.getInputTokens()).isEqualTo(812);
        assertThat(callLog.getOutputTokens()).isEqualTo(96);
        assertThat(callLog.getEstimatedCostUsd()).isNull();
        assertThat(callLog.getKeyLabel()).isNull();
    }

    @Test
    void recordsSuccessWithoutTokensWhenUsageIsUnknown() {
        service.recordSuccess(ANTHROPIC_ATTEMPT, null);

        AiCallLog callLog = captureSavedCallLog();
        assertThat(callLog.isSuccess()).isTrue();
        assertThat(callLog.getInputTokens()).isNull();
        assertThat(callLog.getEstimatedCostUsd()).isNull();
    }

    @Test
    void savesInNewTransaction() {
        service.recordFailure(ANTHROPIC_ATTEMPT, AiFailureKind.UNAUTHORIZED);

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    void swallowsSaveCommitAndPriceLookupFailures() {
        when(aiCallLogRepository.save(any())).thenThrow(new DataAccessResourceFailureException("db down"));
        assertThatCode(() -> service.recordFailure(ANTHROPIC_ATTEMPT, AiFailureKind.SERVER_ERROR)).doesNotThrowAnyException();

        org.mockito.Mockito.doThrow(new UnexpectedRollbackException("rollback-only")).when(transactionManager).commit(any());
        assertThatCode(() -> service.recordFailure(ANTHROPIC_ATTEMPT, AiFailureKind.SERVER_ERROR)).doesNotThrowAnyException();

        when(systemConfigService.getDecimal(any())).thenThrow(new IllegalStateException("config"));
        assertThatCode(() -> service.recordSuccess(ANTHROPIC_ATTEMPT, new AiTokenUsage(1, 1))).doesNotThrowAnyException();
    }

    private AiCallLog captureSavedCallLog() {
        ArgumentCaptor<AiCallLog> savedCallLog = ArgumentCaptor.forClass(AiCallLog.class);
        verify(aiCallLogRepository).save(savedCallLog.capture());
        return savedCallLog.getValue();
    }
}
