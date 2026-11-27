package kr.suhsaechan.secbrief.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 예외 > 로그 글: 제어문자 > ?, 500자 제한, null > unknown, detail 없으면 클래스 이름
 */
class ExceptionLogTextTest {

    @Test
    void sanitizesControlCharactersAndCapsLength() {
        assertThat(ExceptionLogText.toSanitizedErrorMessage("a\nb\u0000c")).isEqualTo("a?b?c");
        assertThat(ExceptionLogText.toSanitizedErrorMessage("x".repeat(600))).hasSize(500);
        assertThat(ExceptionLogText.toSanitizedErrorMessage(null)).isEqualTo("unknown");
    }

    @Test
    void usesSanitizedDetailOfCustomException() {
        CustomException exception = new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "upstream 502\r\nforged line" + "y".repeat(600));

        String logCause = ExceptionLogText.toLogCause(exception);

        assertThat(logCause).startsWith("upstream 502??forged line").hasSize(500);
    }

    @Test
    void fallsBackToClassNameWithoutDetail() {
        assertThat(ExceptionLogText.toLogCause(new CustomException(ErrorCode.INTERNAL_SERVER_ERROR))).isEqualTo("CustomException");
        assertThat(ExceptionLogText.toLogCause(new IllegalStateException("secret message"))).isEqualTo("IllegalStateException");
    }
}
