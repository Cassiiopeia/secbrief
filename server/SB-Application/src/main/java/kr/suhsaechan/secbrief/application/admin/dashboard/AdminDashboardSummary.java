package kr.suhsaechan.secbrief.application.admin.dashboard;

import java.math.BigDecimal;

/**
 * 대시보드 숫자 묶음 (서울 날짜 기준 오늘·이번 주): 수집, AI 대기, AI 호출, 알림 기기, 앱 요청
 */
public record AdminDashboardSummary(
        long todayCollectedCount,
        long weekCollectedCount,
        long pendingTitleAnalysisCount,
        long pendingSummaryCount,
        long todayAiCallCount,
        long todayAiFailureCount,
        BigDecimal todayAiEstimatedCostUsd,
        long pushDeviceCount,
        // 화면 요청 수 (이미지 요청 제외)
        long todayScreenRequestCount,
        long todayImageRequestCount
) {
}
