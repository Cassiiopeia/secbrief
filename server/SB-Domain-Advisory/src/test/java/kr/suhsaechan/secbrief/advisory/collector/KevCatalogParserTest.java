package kr.suhsaechan.secbrief.advisory.collector;

import static java.nio.charset.StandardCharsets.UTF_8;
import static kr.suhsaechan.secbrief.advisory.collector.KevCatalogParser.parse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * KEV 카탈로그 파싱: 항목 > 수집 원본 매핑, since 이전 항목 버림, 형식 오류
 */
class KevCatalogParserTest {

    @Test
    void keepsOnlyEntriesAddedSinceWindow() {
        List<CollectedArticle> articles = parse(fixture(), Instant.parse("2026-10-24T00:00:00Z"));

        assertThat(articles).extracting(CollectedArticle::cveId).containsExactly("CVE-2026-1111", "CVE-2026-2222");
    }

    @Test
    void mapsKevEntryToArticle() {
        CollectedArticle article = parse(fixture(), Instant.EPOCH).get(0);

        assertThat(article.title()).isEqualTo(
                "CVE-2026-1111: ExampleVendor ExampleServer Deserialization of Untrusted Data Vulnerability");
        assertThat(article.originalUrl())
                .isEqualTo("https://www.cisa.gov/known-exploited-vulnerabilities-catalog?search_api_fulltext=CVE-2026-1111");
        assertThat(article.cveId()).isEqualTo("CVE-2026-1111");
        assertThat(article.isKev()).isTrue();
        assertThat(article.cvssScore()).isNull();
        assertThat(article.cvssVersion()).isNull();
        assertThat(article.publishedAt()).isEqualTo(Instant.parse("2026-11-20T00:00:00Z"));
        assertThat(article.publisherName()).isEqualTo("CISA KEV");
    }

    @Test
    void keepsEntryAddedExactlyAtSinceBoundary() {
        List<CollectedArticle> articles = parse(fixture(), Instant.parse("2026-11-10T00:00:00Z"));

        assertThat(articles).extracting(CollectedArticle::cveId).containsExactly("CVE-2026-1111", "CVE-2026-2222");
    }

    @Test
    void keepsEveryEntryWhenSinceIsNull() {
        assertThat(parse(fixture(), null)).hasSize(3);
    }

    @Test
    void skipsMalformedEntriesAndKeepsValidOnes() {
        String catalog = """
                {"vulnerabilities":[
                  {"vulnerabilityName":"no cve id","dateAdded":"2026-11-20"},
                  {"cveID":"CVE-2026-4444","vulnerabilityName":"bad date","dateAdded":"yesterday"},
                  {"cveID":"CVE-2026-5555","vulnerabilityName":"valid entry","dateAdded":"2026-11-21"}
                ]}""";

        assertThat(parse(stream(catalog), null)).extracting(CollectedArticle::cveId).containsExactly("CVE-2026-5555");
    }

    @Test
    void throwsFeedParseErrorWhenVulnerabilitiesMissing() {
        assertFeedParseError(() -> parse(stream("{\"title\":\"x\"}"), null));
    }

    @Test
    void throwsFeedParseErrorWhenEveryEntryIsMalformed() {
        // 필드 이름이 바뀐 경우: 조용히 0건이 되지 않고 실패로 드러나야 함
        assertFeedParseError(() -> parse(stream("{\"vulnerabilities\":[{\"cve\":\"CVE-2026-1\"},{\"cve\":\"CVE-2026-2\"}]}"), null));
    }

    @Test
    void returnsEmptyListWhenCatalogHasNoEntries() {
        assertThat(parse(stream("{\"vulnerabilities\":[]}"), null)).isEmpty();
    }

    @Test
    void throwsFeedParseErrorOnBrokenJson() {
        assertFeedParseError(() -> parse(stream("{\"vulnerabilities\":["), null));
    }

    private static void assertFeedParseError(Runnable parsing) {
        assertThatThrownBy(parsing::run)
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FEED_PARSE_ERROR);
    }

    private static InputStream fixture() {
        return KevCatalogParserTest.class.getResourceAsStream("/fixtures/kev/kev-catalog.json");
    }

    private static InputStream stream(String json) {
        return new ByteArrayInputStream(json.getBytes(UTF_8));
    }
}
