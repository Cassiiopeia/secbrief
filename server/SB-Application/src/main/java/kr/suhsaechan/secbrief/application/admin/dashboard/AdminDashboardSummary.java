package kr.suhsaechan.secbrief.application.admin.dashboard;

import java.math.BigDecimal;

/**
 * 대시보드 숫자 묶음 (서울 날짜 기준 오늘·이번 주): 수집, AI 대기·실패, AI 호출, 알림 발송, 공지, 알림 기기, 앱 요청
 */
public record AdminDashboardSummary(
        long todayCollectedCount,
        long weekCollectedCount,
        long pendingTitleAnalysisCount,
        long pendingSummaryCount,
        // 요약 시도를 다 쓰고 실패로 남은 기사 수 (기간 무관)
        long failedSummaryCount,
        long todayAiCallCount,
        long todayAiFailureCount,
        BigDecimal todayAiEstimatedCostUsd,
        // 오늘 푸시를 보낸 긴급 알림·공지 수 (방해 금지로 전부 보류 중인 건 빠짐)
        long todayPushedAlertCount,
        long todayPushedNoticeCount,
        // 방해 금지 시간이라 기기별로 보류 중인 푸시 수
        long heldPushCount,
        long noticeCount,
        long pushDeviceCount,
        // 화면 요청 수 (이미지 요청 제외)
        long todayScreenRequestCount,
        long todayImageRequestCount
) {

    public long pendingAiCount() {
        return pendingTitleAnalysisCount + pendingSummaryCount;
    }

    public long todayPushedCount() {
        return todayPushedAlertCount + todayPushedNoticeCount;
    }
}
