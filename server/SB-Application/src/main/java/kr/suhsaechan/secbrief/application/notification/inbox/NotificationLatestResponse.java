package kr.suhsaechan.secbrief.application.notification.inbox;

/**
 * 종류별 가장 최근 알림함 번호 (앱 알림 점: 기기에 저장한 본 번호보다 크면 새 알림, 없으면 null)
 */
public record NotificationLatestResponse(Long latestAlertId, Long latestNoticeId) {
}
