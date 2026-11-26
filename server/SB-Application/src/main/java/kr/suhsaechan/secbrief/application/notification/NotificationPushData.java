package kr.suhsaechan.secbrief.application.notification;

import java.util.HashMap;
import java.util.Map;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;

/**
 * 푸시 data 키·값 단일 원본 (앱 mobile 의 알림 이동 규칙과 같아야 함)
 * 키 4개: notificationId(알림함 번호), notificationKind, articleId(기사 1건 알림만), targetTab(이전 앱용, 항상)
 */
public final class NotificationPushData {

    public static final String NOTIFICATION_ID_KEY = "notificationId";
    // ALERT, NOTICE, DIGEST(아침 요약), QUIET_HOURS_DIGEST
    public static final String NOTIFICATION_KIND_KEY = "notificationKind";
    public static final String ARTICLE_ID_KEY = "articleId";
    public static final String TARGET_TAB_KEY = "targetTab";
    // 방해 금지 시간이 끝나고 보내는 모음 알림 (앱이 알림함을 엶)
    public static final String QUIET_HOURS_DIGEST_KIND = "QUIET_HOURS_DIGEST";
    // targetTab 값: 긴급 소식은 취약점 탭, 공지는 오늘 탭 (이전 앱은 vulnerability 가 아니면 오늘 탭을 엶)
    public static final String TARGET_TAB_VULNERABILITY = "vulnerability";
    public static final String TARGET_TAB_TODAY = "today";

    private NotificationPushData() {
    }

    /**
     * 알림함 1건 > data (articleId 는 값 있을 때만)
     */
    public static Map<String, String> forNotification(long notificationId, NotificationKind notificationKind,
                                                      Long articleId) {
        Map<String, String> pushData = new HashMap<>();
        pushData.put(NOTIFICATION_ID_KEY, String.valueOf(notificationId));
        pushData.put(NOTIFICATION_KIND_KEY, notificationKind.name());
        pushData.put(TARGET_TAB_KEY, toTargetTab(notificationKind));
        if (articleId != null) {
            pushData.put(ARTICLE_ID_KEY, String.valueOf(articleId));
        }
        return Map.copyOf(pushData);
    }

    /**
     * 방해 금지 시간 모음 알림 > data (공지만 모였으면 오늘 탭, 그 밖은 취약점 탭)
     */
    public static Map<String, String> forQuietHoursDigest(boolean hasAlert) {
        return Map.of(NOTIFICATION_KIND_KEY, QUIET_HOURS_DIGEST_KIND,
                TARGET_TAB_KEY, hasAlert ? TARGET_TAB_VULNERABILITY : TARGET_TAB_TODAY);
    }

    // 공지만 오늘 탭 (알림·아침 요약은 취약점 탭)
    private static String toTargetTab(NotificationKind notificationKind) {
        return notificationKind == NotificationKind.NOTICE ? TARGET_TAB_TODAY : TARGET_TAB_VULNERABILITY;
    }
}
