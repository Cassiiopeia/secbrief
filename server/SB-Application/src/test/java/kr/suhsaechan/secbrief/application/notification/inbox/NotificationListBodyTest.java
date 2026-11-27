package kr.suhsaechan.secbrief.application.notification.inbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import kr.suhsaechan.secbrief.member.entity.Notification;
import org.junit.jupiter.api.Test;

/**
 * 알림함 목록 본문: 공지만 앞 120자 미리 보기, 이모지를 반으로 가르지 않음
 */
class NotificationListBodyTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-27T00:00:00Z");

    @Test
    void cutsNoticeBodyToPreviewLength() {
        Notification notice = Notification.createNotice("점검", "가".repeat(200), null, false, CREATED_AT);

        assertThat(NotificationQueryService.toListBody(notice)).isEqualTo("가".repeat(NotificationQueryService.NOTICE_PREVIEW_LENGTH));
    }

    @Test
    void keepsShortNoticeBody() {
        Notification notice = Notification.createNotice("점검", "짧은 본문", null, false, CREATED_AT);

        assertThat(NotificationQueryService.toListBody(notice)).isEqualTo("짧은 본문");
    }

    @Test
    void dropsHalfOfEmojiAtPreviewBoundary() {
        // 119자 + 이모지(2칸) > 120칸째가 이모지 앞 절반
        Notification notice = Notification.createNotice("점검", "가".repeat(119) + "🔒" + "나", null, false, CREATED_AT);

        assertThat(NotificationQueryService.toListBody(notice)).isEqualTo("가".repeat(119));
    }
}
