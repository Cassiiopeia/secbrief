package kr.suhsaechan.secbrief.advisory.collector;

import static java.nio.charset.StandardCharsets.UTF_8;
import static kr.suhsaechan.secbrief.advisory.collector.GhsaAdvisoryParser.parse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * GitHub 보안 권고: 요약·링크·CVE·점수 버전, since 이전 버림, 배열 아니면 FEED_PARSE_ERROR
 */
class GhsaAdvisoryParserTest {

    private static final Instant SINCE = Instant.parse("2026-11-16T00:00:00Z");

    @Test
    void mapsAdvisoryWithCveAndV3Score() {
        CollectedArticle article = parse(openFixture(), SINCE).get(0);

        assertThat(article.title()).isEqualTo("Example Web Framework: remote code execution in request parser");
        assertThat(article.cveId()).isEqualTo("CVE-2026-5555");
        assertThat(article.originalUrl()).isEqualTo("https://github.com/advisories/GHSA-aaaa-bbbb-cccc");
        assertThat(article.publisherName()).isEqualTo("GitHub Advisory");
        assertThat(article.cvssScore()).isEqualByComparingTo("9.8");
        assertThat(article.cvssVersion()).isEqualTo("3.1");
        assertThat(article.publishedAt()).isEqualTo(Instant.parse("2026-11-21T10:00:00Z"));
        assertThat(article.isKev()).isFalse();
    }

    @Test
    void keepsAdvisoryWithoutCveUsingHtmlUrl() {
        CollectedArticle article = parse(openFixture(), SINCE).get(1);

        assertThat(article.cveId()).isNull();
        assertThat(article.originalUrl()).isEqualTo("https://github.com/advisories/GHSA-dddd-eeee-ffff");
        assertThat(article.cvssScore()).isEqualByComparingTo("9.3");
        assertThat(article.cvssVersion()).isEqualTo("4.0");
    }

    @Test
    void dropsAdvisoriesPublishedBeforeSince() {
        assertThat(parse(openFixture(), SINCE)).hasSize(2);
        assertThat(parse(openFixture(), null)).hasSize(3);
    }

    @Test
    void leavesScoreNullWhenBothScoresAreZero() {
        String json = """
                [{"summary":"s","html_url":"https://github.com/advisories/GHSA-x","published_at":"2026-11-20T00:00:00Z",
                  "cve_id":null,"cvss_severities":{"cvss_v3":{"vector_string":null,"score":0.0},"cvss_v4":{"vector_string":null,"score":0.0}}}]
                """;

        CollectedArticle article = parse(toInputStream(json), null).get(0);

        assertThat(article.cvssScore()).isNull();
        assertThat(article.cvssVersion()).isNull();
    }

    @Test
    void throwsFeedParseErrorWhenNotArray() {
        assertThatThrownBy(() -> parse(toInputStream("{\"message\":\"API rate limit exceeded\"}"), null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FEED_PARSE_ERROR);
    }

    private static InputStream openFixture() {
        return GhsaAdvisoryParserTest.class.getResourceAsStream("/fixtures/ghsa/ghsa-advisories.json");
    }

    private static InputStream toInputStream(String json) {
        return new ByteArrayInputStream(json.getBytes(UTF_8));
    }
}
