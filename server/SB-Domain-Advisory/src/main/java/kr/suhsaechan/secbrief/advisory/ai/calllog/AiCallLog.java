package kr.suhsaechan.secbrief.advisory.ai.calllog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 호출 기록 1건: 용도, 제공자, 모델, 키 별명, 성공 여부·실패 종류, 걸린 시간, 토큰, 추정 비용
 * 입력 글·응답 글·키 값은 남기지 않음
 */
@Entity
@Table(name = "ai_call_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiCallType callType;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(length = 100)
    private String model;

    @Column(length = 50)
    private String keyLabel;

    @Column(nullable = false)
    private boolean isSuccess;

    // AiFailureKind 이름 (성공이면 null)
    @Column(length = 100)
    private String failureReason;

    @Column(name = "latency_ms")
    private Integer latencyMilliseconds;

    private Integer inputTokens;

    private Integer outputTokens;

    @Column(precision = 12, scale = 6)
    private BigDecimal estimatedCostUsd;

    @Column(nullable = false)
    private Instant createdAt;

    public static AiCallLog succeeded(AiCallAttempt attempt, AiTokenUsage tokenUsage, BigDecimal estimatedCostUsd,
                                      Instant createdAt) {
        AiCallLog callLog = of(attempt, createdAt);
        callLog.isSuccess = true;
        callLog.inputTokens = tokenUsage.inputTokens();
        callLog.outputTokens = tokenUsage.outputTokens();
        callLog.estimatedCostUsd = estimatedCostUsd;
        return callLog;
    }

    public static AiCallLog failed(AiCallAttempt attempt, AiFailureKind failureKind, Instant createdAt) {
        AiCallLog callLog = of(attempt, createdAt);
        callLog.isSuccess = false;
        callLog.failureReason = failureKind.name();
        return callLog;
    }

    private static AiCallLog of(AiCallAttempt attempt, Instant createdAt) {
        AiCallLog callLog = new AiCallLog();
        callLog.callType = attempt.callType();
        callLog.provider = attempt.providerCode();
        callLog.model = attempt.model();
        callLog.keyLabel = attempt.keyLabel();
        callLog.latencyMilliseconds = (int) Math.min(attempt.latencyMilliseconds(), Integer.MAX_VALUE);
        callLog.createdAt = createdAt;
        return callLog;
    }
}
