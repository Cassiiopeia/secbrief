package kr.suhsaechan.secbrief.advisory.policy;

import static kr.suhsaechan.secbrief.advisory.policy.SourceTextPolicy.toSourceText;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 요약 입력 정리: HTML·코드 블록 제거, 문자 표기 풀기, 공백 정리, 6000자 제한, 쓸 만하지 않으면 null (40자 미만·제목에 들어 있는 글)
 */
class SourceTextPolicyTest {

    private static final String TITLE = "Example VPN flaw exploited";

    @Test
    void stripsTagsScriptsAndEntities() {
        String rawSourceText = """
                <style>p { color: red; }</style><p>Example&nbsp;Corp says &quot;no customer data&quot; was taken.</p>
                <script>alert('x')</script><p>The flaw affects versions &lt; 2.4.1 &#8212; patch now.</p>
                """;

        assertThat(toSourceText(rawSourceText, TITLE)).isEqualTo(
                "Example Corp says \"no customer data\" was taken. The flaw affects versions < 2.4.1 — patch now.");
    }

    @Test
    void keepsSentencesApartWhenTagsAreRemoved() {
        String rawSourceText = "<p>First sentence about the incident at Example Corp.</p><p>Second sentence here.</p>";

        assertThat(toSourceText(rawSourceText, TITLE))
                .isEqualTo("First sentence about the incident at Example Corp. Second sentence here.");
    }

    @Test
    void truncatesToMaximumLengthWithoutSplittingSurrogatePair() {
        String longText = "가".repeat(SourceTextPolicy.MAXIMUM_SOURCE_TEXT_LENGTH - 1) + "😀" + "나".repeat(10);

        String sourceText = toSourceText(longText, TITLE);

        assertThat(sourceText).hasSize(SourceTextPolicy.MAXIMUM_SOURCE_TEXT_LENGTH - 1).endsWith("가");
    }

    @Test
    void returnsNullForMissingShortOrTitleOnlyText() {
        assertThat(toSourceText(null, TITLE)).isNull();
        assertThat(toSourceText("   <br/> ", TITLE)).isNull();
        assertThat(toSourceText("a".repeat(SourceTextPolicy.MINIMUM_SOURCE_TEXT_LENGTH - 1), TITLE)).isNull();
        assertThat(toSourceText("a".repeat(SourceTextPolicy.MINIMUM_SOURCE_TEXT_LENGTH), TITLE)).isNotNull();
        // 제목에 통째로 들어 있는 글 (대소문자 무시)
        String title = "CVE-2026-0002: Example Library 4.0 deserialization flaw in the parser.";
        assertThat(toSourceText("example library 4.0 DESERIALIZATION flaw in the parser.", title)).isNull();
    }
}
