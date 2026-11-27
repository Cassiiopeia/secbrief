package kr.suhsaechan.secbrief.application.notification.notice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 공지 푸시 본문: 줄바꿈 > 공백, 120자 넘으면 119자 + …, 이모지를 반으로 가르지 않음
 */
class AdminNoticePushBodyTest {

    @Test
    void flattensWhitespaceAndKeepsShortBody() {
        assertThat(AdminNoticeService.toPushBody(" 점검\n안내\t입니다 ")).isEqualTo("점검 안내 입니다");
    }

    @Test
    void cutsLongBodyWithEllipsis() {
        String pushBody = AdminNoticeService.toPushBody("가".repeat(200));

        assertThat(pushBody).hasSize(AdminNoticeService.PUSH_BODY_LENGTH).endsWith("…");
    }

    @Test
    void dropsHalfOfEmojiAtCutBoundary() {
        // 118자 + 이모지(2칸) > 119칸째가 이모지 앞 절반
        String pushBody = AdminNoticeService.toPushBody("가".repeat(118) + "🔒" + "나".repeat(10));

        assertThat(pushBody).isEqualTo("가".repeat(118) + "…");
    }
}
