package kr.suhsaechan.secbrief.application.notification.inbox;

import java.util.List;

/**
 * 알림함 목록 1페이지: 항목 + 다음 위치 커서 (끝이면 null)
 */
public record NotificationPageResponse(List<NotificationSummaryResponse> items, String nextCursor) {
}
