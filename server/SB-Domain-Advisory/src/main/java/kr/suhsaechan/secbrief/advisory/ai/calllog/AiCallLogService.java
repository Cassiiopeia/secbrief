package kr.suhsaechan.secbrief.advisory.ai.calllog;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.function.Supplier;
import kr.suhsaechan.secbrief.advisory.constant.AiProviderCode;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI 호출 기록 저장: 호출 1회 > 1행 (성공: 토큰·추정 비용, 실패: 실패 종류)
 * 별도 트랜잭션으로 저장하고 기록 실패는 삼킴 (기록 때문에 AI 처리가 멈추지 않게)
 */
@Slf4j
@Service
public class AiCallLogService {

    private final AiCallLogRepository aiCallLogRepository;
    private final SystemConfigService systemConfigService;
    private final Clock clock;
    // 항상 새 트랜잭션 (호출부 트랜잭션과 묶이지 않음, 커밋 실패까지 여기서 잡음)
    private final TransactionTemplate newTransactionTemplate;

    public AiCallLogService(AiCallLogRepository aiCallLogRepository, SystemConfigService systemConfigService,
                            PlatformTransactionManager transactionManager, Clock clock) {
        this.aiCallLogRepository = aiCallLogRepository;
        this.systemConfigService = systemConfigService;
        this.clock = clock;
        this.newTransactionTemplate = new TransactionTemplate(transactionManager);
        this.newTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void recordSuccess(AiCallAttempt attempt, AiTokenUsage tokenUsage) {
        save(attempt, () -> {
            AiTokenUsage recordedUsage = tokenUsage == null ? AiTokenUsage.UNKNOWN : tokenUsage;
            return AiCallLog.succeeded(attempt, recordedUsage, estimateCost(attempt.providerCode(), recordedUsage),
                    clock.instant());
        });
    }

    public void recordFailure(AiCallAttempt attempt, AiFailureKind failureKind) {
        save(attempt, () -> AiCallLog.failed(attempt, failureKind, clock.instant()));
    }

    // 단가를 아는 제공자(Anthropic)만 추정, 그 밖(자체 서버 Ollama)은 null
    private BigDecimal estimateCost(String providerCode, AiTokenUsage tokenUsage) {
        if (!AiProviderCode.ANTHROPIC.equals(providerCode)) {
            return null;
        }
        return AiCostCalculator.estimate(tokenUsage,
                systemConfigService.getDecimal(ConfigKey.PRICE_ANTHROPIC_INPUT_PER_1M),
                systemConfigService.getDecimal(ConfigKey.PRICE_ANTHROPIC_OUTPUT_PER_1M));
    }

    private void save(AiCallAttempt attempt, Supplier<AiCallLog> callLogSupplier) {
        try {
            AiCallLog callLog = callLogSupplier.get();
            newTransactionTemplate.executeWithoutResult(transactionStatus -> aiCallLogRepository.save(callLog));
        } catch (RuntimeException exception) {
            log.warn("[ai call log] record failed 500 : callType={}, provider={}, cause={}",
                    attempt.callType(), attempt.providerCode(), exception.getClass().getSimpleName());
        }
    }
}
