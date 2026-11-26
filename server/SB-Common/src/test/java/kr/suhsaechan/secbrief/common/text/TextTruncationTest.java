package kr.suhsaechan.secbrief.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextTruncationTest {

    @Test
    void returnsSameTextWhenWithinMaximumLength() {
        assertThat(TextTruncation.truncateKeepingSurrogatePairs("abc", 3)).isEqualTo("abc");
        assertThat(TextTruncation.truncateKeepingSurrogatePairs("", 3)).isEmpty();
    }

    @Test
    void cutsAtMaximumLength() {
        assertThat(TextTruncation.truncateKeepingSurrogatePairs("abcdef", 4)).isEqualTo("abcd");
    }

    @Test
    void dropsHalfSurrogatePairAtCutEnd() {
        // 3칸 + 이모지(2칸): 4칸에서 자르면 앞 절반만 남는 위치
        assertThat(TextTruncation.truncateKeepingSurrogatePairs("abc😀d", 4)).isEqualTo("abc");
        assertThat(TextTruncation.truncateKeepingSurrogatePairs("abc😀d", 5)).isEqualTo("abc😀");
    }
}
