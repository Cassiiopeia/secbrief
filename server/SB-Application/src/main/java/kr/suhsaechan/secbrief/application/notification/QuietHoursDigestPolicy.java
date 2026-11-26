package kr.suhsaechan.secbrief.application.notification;

import java.util.List;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import kr.suhsaechan.secbrief.member.entity.HeldPush;
import kr.suhsaechan.secbrief.member.push.PushMessage;

/**
 * 방해 금지 시간이 끝난 기기 1대의 보류 푸시 > 보낼 메시지 1건
 * 1건이면 그 글 그대로, 여러 건이면 건수만 모은 1건 (긴급 소식은 기사 수, 공지는 공지 수로 셈)
 */
public final class QuietHoursDigestPolicy {

    static final String DIGEST_BODY = "알림함에서 한 번에 모아 볼 수 있어요";

    private QuietHoursDigestPolicy() {
    }

    public static PushMessage toPushMessage(String pushToken, List<HeldPush> heldPushes) {
        if (heldPushes.size() == 1) {
            return PushContent.from(heldPushes.get(0)).toPushMessage(pushToken);
        }
        int alertArticleCount = heldPushes.stream()
                .filter(heldPush -> heldPush.getNotificationKind() == NotificationKind.ALERT)
                .mapToInt(HeldPush::getArticleCount)
                .sum();
        int noticeCount = (int) heldPushes.stream()
                .filter(heldPush -> heldPush.getNotificationKind() == NotificationKind.NOTICE)
                .count();
        return new PushMessage(pushToken, toDigestTitle(alertArticleCount, noticeCount), DIGEST_BODY,
                NotificationPushData.forQuietHoursDigest(alertArticleCount > 0), UrgentAlertPolicy.ANDROID_CHANNEL_ID);
    }

    /**
     * 모음 제목: 긴급 소식만 / 공지만 / 둘 다
     */
    static String toDigestTitle(int alertArticleCount, int noticeCount) {
        if (noticeCount == 0) {
            return "방해 금지 시간에 긴급 소식 " + alertArticleCount + "건이 들어왔어요";
        }
        if (alertArticleCount == 0) {
            return "방해 금지 시간에 공지사항 " + noticeCount + "건이 올라왔어요";
        }
        return "방해 금지 시간에 긴급 소식 " + alertArticleCount + "건, 공지사항 " + noticeCount + "건이 있었어요";
    }
}
