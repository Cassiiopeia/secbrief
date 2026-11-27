package kr.suhsaechan.secbrief.application.notification.notice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 공지사항 입력 정리: 본문 줄바꿈(CRLF·CR) > LF, 앞뒤 공백 제거 (#87)
 */
class NoticeFormTest {

    @Test
    void normalizesCrLfAndCrLineBreaksToLf() {
        NoticeForm validatedForm = new NoticeForm(" 점검 안내 ", "  첫 줄\r\n둘째 줄\r셋째 줄\n넷째 줄\r\n", "", false).toValidated();

        assertThat(validatedForm.title()).isEqualTo("점검 안내");
        assertThat(validatedForm.body()).isEqualTo("첫 줄\n둘째 줄\n셋째 줄\n넷째 줄").doesNotContain("\r");
        assertThat(validatedForm.linkUrl()).isNull();
    }
}
