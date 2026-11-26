package kr.suhsaechan.secbrief.application.notification;

import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import kr.suhsaechan.secbrief.member.entity.HeldPush;
import kr.suhsaechan.secbrief.member.push.PushMessage;

/**
 * 기기 1대가 받을 알림함 푸시 글: 알림함 번호·종류, 제목, 본문, 기사 번호(1건 알림만), 알린 기사 수(공지는 0)
 * 바로 보내거나, 방해 금지 시간이면 그대로 보류했다가 같은 글로 보냄
 */
public record PushContent(long notificationId, NotificationKind notificationKind, String title, String body,
                          Long articleId, int articleCount) {

    public static PushContent from(HeldPush heldPush) {
        return new PushContent(heldPush.getNotificationId(), heldPush.getNotificationKind(), heldPush.getTitle(),
                heldPush.getBody(), heldPush.getArticleId(), heldPush.getArticleCount());
    }

    public PushMessage toPushMessage(String pushToken) {
        return new PushMessage(pushToken, title, body,
                NotificationPushData.forNotification(notificationId, notificationKind, articleId),
                UrgentAlertPolicy.ANDROID_CHANNEL_ID);
    }
}
