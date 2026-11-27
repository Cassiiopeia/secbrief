package kr.suhsaechan.secbrief.application.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import kr.suhsaechan.secbrief.member.entity.HeldPush;
import kr.suhsaechan.secbrief.member.push.PushMessage;
import org.junit.jupiter.api.Test;

/**
 * 방해 금지 모음 알림: 1건이면 그대로(번호·기사 번호 유지), 여러 건이면 건수만 (긴급 소식은 기사 수, 공지는 공지 수)
 */
class QuietHoursDigestPolicyTest {

    private static final String PUSH_TOKEN = "ExponentPushToken[digest]";
    private static final Instant HELD_AT = Instant.parse("2026-11-26T15:00:00Z");

    @Test
    void sendsSingleHeldPushAsIs() {
        PushMessage pushMessage = QuietHoursDigestPolicy.toPushMessage(PUSH_TOKEN,
                List.of(heldAlert(11L, "악용 중인 취약점이 확인됐어요", 501L, 1)), Set.of());

        assertThat(pushMessage.title()).isEqualTo("악용 중인 취약점이 확인됐어요");
        assertThat(pushMessage.data()).containsEntry("notificationId", "11").containsEntry("articleId", "501")
                .containsEntry("notificationKind", "ALERT").doesNotContainKey("targetScreen");
    }

    @Test
    void opensAlertLevelScreenForSingleHeldAlertLevelArticle() {
        // 보류분에는 출처가 없어 보낼 때 받은 경보단계 글 번호로 판정 (#87)
        PushMessage pushMessage = QuietHoursDigestPolicy.toPushMessage(PUSH_TOKEN,
                List.of(heldAlert(13L, "KISA 긴급 공지가 올라왔어요", 601L, 1)), Set.of(601L));

        assertThat(pushMessage.data()).containsEntry("articleId", "601").containsEntry("targetScreen", "alertLevel");
    }

    @Test
    void sendsOneDigestCountingArticlesForSeveralHeldAlerts() {
        PushMessage pushMessage = QuietHoursDigestPolicy.toPushMessage(PUSH_TOKEN, List.of(
                heldAlert(11L, "긴급 보안 소식 3건이 들어왔어요", null, 3),
                heldAlert(12L, "고위험 취약점이 공개됐어요", 777L, 1)), Set.of(777L));

        assertThat(pushMessage.title()).isEqualTo("방해 금지 시간에 긴급 소식 4건이 들어왔어요");
        assertThat(pushMessage.body()).isEqualTo(QuietHoursDigestPolicy.DIGEST_BODY);
        assertThat(pushMessage.data()).containsEntry("notificationKind", "QUIET_HOURS_DIGEST")
                .containsEntry("targetTab", "vulnerability").doesNotContainKeys("notificationId", "articleId", "targetScreen");
    }

    @Test
    void writesDigestTitleForNoticesAndMixedItems() {
        assertThat(QuietHoursDigestPolicy.toDigestTitle(0, 2)).isEqualTo("방해 금지 시간에 공지사항 2건이 올라왔어요");
        assertThat(QuietHoursDigestPolicy.toDigestTitle(5, 1)).isEqualTo("방해 금지 시간에 긴급 소식 5건, 공지사항 1건이 있었어요");
        assertThat(QuietHoursDigestPolicy.toPushMessage(PUSH_TOKEN, List.of(heldNotice(21L), heldNotice(22L)), Set.of()).data())
                .containsEntry("targetTab", "today");
    }

    private static HeldPush heldAlert(long notificationId, String title, Long articleId, int articleCount) {
        return HeldPush.create(null, notificationId, NotificationKind.ALERT, title, "본문", articleId, articleCount, HELD_AT);
    }

    private static HeldPush heldNotice(long notificationId) {
        return HeldPush.create(null, notificationId, NotificationKind.NOTICE, "서비스 점검 안내", "본문", null, 0, HELD_AT);
    }
}
