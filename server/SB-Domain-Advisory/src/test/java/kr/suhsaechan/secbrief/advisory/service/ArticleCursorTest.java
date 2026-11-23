package kr.suhsaechan.secbrief.advisory.service;

import static kr.suhsaechan.secbrief.advisory.service.ArticleCursor.decode;
import static kr.suhsaechan.secbrief.advisory.service.ArticleCursor.encode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Base64;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 커서: 왕복 변환, 깨진 값은 INVALID_CURSOR
 */
class ArticleCursorTest {

    @Test
    void roundTripsCursor() {
        Instant publishedAt = Instant.parse("2026-11-23T01:02:03Z");

        assertThat(decode(encode(publishedAt, 42))).isEqualTo(new ArticleCursor(publishedAt, 42));
    }

    @Test
    void encodesUrlSafeWithoutPadding() {
        assertThat(encode(Instant.parse("2026-11-23T01:02:03Z"), 42)).doesNotContain("=", "+", "/");
    }

    @Test
    void rejectsGarbageCursorWithInvalidCursorCode() {
        assertThatThrownBy(() -> decode("not-a-cursor")).extracting("errorCode").isEqualTo(ErrorCode.INVALID_CURSOR);
        String threeParts = Base64.getUrlEncoder().withoutPadding().encodeToString("1:2:3".getBytes());
        assertThatThrownBy(() -> decode(threeParts)).extracting("errorCode").isEqualTo(ErrorCode.INVALID_CURSOR);
    }
}
