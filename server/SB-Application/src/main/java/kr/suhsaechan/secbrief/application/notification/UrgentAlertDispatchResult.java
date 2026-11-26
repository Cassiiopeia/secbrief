package kr.suhsaechan.secbrief.application.notification;

/**
 * 긴급 알림 틱 결과: 처리한 기사 수, 접수된 기기 수, 실패한 기기 수, 지운 무효 기기 수
 * isRetryPending: 발송이 전부 실패해 기사를 대기로 남김 (다음 틱에 다시)
 */
public record UrgentAlertDispatchResult(int articleCount, int acceptedCount, int failedCount,
                                        int unregisteredCount, boolean isRetryPending) {

    public static UrgentAlertDispatchResult nothingToSend() {
        return new UrgentAlertDispatchResult(0, 0, 0, 0, false);
    }
}
