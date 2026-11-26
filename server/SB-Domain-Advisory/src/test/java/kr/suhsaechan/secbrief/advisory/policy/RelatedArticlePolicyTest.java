package kr.suhsaechan.secbrief.advisory.policy;

import static kr.suhsaechan.secbrief.advisory.policy.RelatedArticlePolicy.toCveIdPattern;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 관련 기사용 CVE 정규식: 전용 열 + 제목의 CVE ID, 중복 제거, 없으면 빈 문자열, 뒤에 숫자가 이어지는 번호는 제외
 */
class RelatedArticlePolicyTest {

    @Test
    void buildsPatternFromColumnAndTitle() {
        String cveIdPattern = toCveIdPattern("CVE-2026-1111", "cve-2026-1111 and CVE-2026-22222 exploited in the wild");

        assertThat(cveIdPattern).isEqualTo("(CVE-2026-1111|CVE-2026-22222)(?!\\d)");
    }

    @Test
    void returnsEmptyPatternWhenArticleHasNoCve() {
        assertThat(toCveIdPattern(null, "Example Corp confirms data breach")).isEmpty();
    }

    @Test
    void doesNotMatchLongerCveNumber() {
        Pattern cveIdPattern = Pattern.compile(toCveIdPattern("CVE-2026-1111", "제목"), Pattern.CASE_INSENSITIVE);

        assertThat(cveIdPattern.matcher("Patch for cve-2026-1111 released").find()).isTrue();
        assertThat(cveIdPattern.matcher("Patch for CVE-2026-11112 released").find()).isFalse();
    }
}
