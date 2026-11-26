package kr.suhsaechan.secbrief.application.admin.stats;

import java.math.BigDecimal;

/**
 * AI 호출 집계 1행: 칸 첫날(yyyy-MM-dd), 성공 수, 실패 수, 추정 비용(USD)
 */
public record AdminAiUsageBucket(String bucketStart, long successCount, long failureCount, BigDecimal estimatedCostUsd) {
}
