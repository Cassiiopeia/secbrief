package kr.suhsaechan.secbrief.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 관리자 감사 기록 값 자르기: null 그대로, 짧으면 그대로, 이모지를 반으로 가르지 않음
 */
class AdminAuditTruncationTest {

    @Test
    void keepsNullAndShortText() {
        assertThat(AdminAuditService.truncate(null, 5)).isNull();
        assertThat(AdminAuditService.truncate("abc", 5)).isEqualTo("abc");
    }

    @Test
    void cutsLongTextWithoutSplittingEmoji() {
        assertThat(AdminAuditService.truncate("abcdefg", 5)).isEqualTo("abcde");
        assertThat(AdminAuditService.truncate("abcd🔒", 5)).isEqualTo("abcd");
    }
}
