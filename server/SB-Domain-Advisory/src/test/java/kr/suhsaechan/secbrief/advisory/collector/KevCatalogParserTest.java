package kr.suhsaechan.secbrief.advisory.collector;

import static java.nio.charset.StandardCharsets.UTF_8;
import static kr.suhsaechan.secbrief.advisory.collector.KevCatalogParser.parse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * KEV 카탈로그 파싱: 항목 > 수집 원본 매핑, since 이전 항목 버림, 형식 오류
 * 요약 입력(설명 + 조치 문구), 사실 값(제조사·제품, 조치 요구, 조치 기한, 랜섬웨어 악용, CWE)은 카탈로그 값 그대로
 */
class KevCatalogParserTest {

    @Test
    void keepsOnlyEntriesAddedSinceWindow() {
        List<CollectedArticle> articles = parse(openFixture(), Instant.parse("2026-10-24T00:00:00Z"));

        assertThat(articles).extracting(CollectedArticle::cveId).containsExactly("CVE-2026-1111", "CVE-2026-2222");
    }

    @Test
    void mapsKevEntryToArticle() {
        CollectedArticle article = parse(openFixture(), Instant.EPOCH).get(0);

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
    void takesSourceTextFromShortDescriptionAndRequiredAction() {
        CollectedArticle article = parse(openFixture(), Instant.EPOCH).get(0);

        assertThat(article.sourceText()).isEqualTo(
                "ExampleVendor ExampleServer contains a deserialization of untrusted data vulnerability that allows a remote attacker to execute arbitrary code."
                        + " Required action: Apply mitigations per vendor instructions or discontinue use of the product if mitigations are unavailable.");
    }

    @Test
    void takesFactsExactlyAsWrittenInCatalog() {
        List<CollectedArticle> articles = parse(openFixture(), Instant.EPOCH);

        assertThat(articles.get(0).sourceFacts()).containsExactly(
                new SourceFact("제조사·제품", "ExampleVendor ExampleServer"),
                new SourceFact("조치 요구", "Apply mitigations per vendor instructions or discontinue use of the product if mitigations are unavailable."),
                new SourceFact("조치 기한", "2026-11-23"),
                new SourceFact("랜섬웨어 악용", "확인 안 됨"),
                new SourceFact("CWE", "CWE-502"));
        assertThat(articles.get(1).sourceFacts()).contains(
                new SourceFact("랜섬웨어 악용", "확인됨"), new SourceFact("CWE", "CWE-287, CWE-306"));
    }

    @Test
    void leavesOutFactsAndSourceTextMissingFromEntry() {
        String catalog = """
                {"vulnerabilities":[{"cveID":"CVE-2026-9999","vulnerabilityName":"Example flaw","dateAdded":"2026-11-20",
                  "dueDate":"2026-12-11"}]}
                """;

        CollectedArticle article = parse(toInputStream(catalog), null).get(0);

        assertThat(article.sourceText()).isNull();
        assertThat(article.sourceFacts()).containsExactly(new SourceFact("조치 기한", "2026-12-11"));
    }

    @Test
    void keepsEntryAddedExactlyAtSinceBoundary() {
        List<CollectedArticle> articles = parse(openFixture(), Instant.parse("2026-11-10T00:00:00Z"));

        assertThat(articles).extracting(CollectedArticle::cveId).containsExactly("CVE-2026-1111", "CVE-2026-2222");
    }

    @Test
    void keepsEveryEntryWhenSinceIsNull() {
        assertThat(parse(openFixture(), null)).hasSize(3);
    }

    @Test
    void skipsMalformedEntriesAndKeepsValidOnes() {
        String catalog = """
                {"vulnerabilities":[
                  {"vulnerabilityName":"no cve id","dateAdded":"2026-11-20"},
                  {"cveID":"CVE-2026-4444","vulnerabilityName":"bad date","dateAdded":"yesterday"},
                  {"cveID":"CVE-2026-5555","vulnerabilityName":"valid entry","dateAdded":"2026-11-21"}
                ]}""";

        assertThat(parse(toInputStream(catalog), null)).extracting(CollectedArticle::cveId).containsExactly("CVE-2026-5555");
    }

    @Test
    void throwsFeedParseErrorWhenVulnerabilitiesMissing() {
        assertFeedParseError(() -> parse(toInputStream("{\"title\":\"x\"}"), null));
    }

    @Test
    void throwsFeedParseErrorWhenEveryEntryIsMalformed() {
        // 필드 이름이 바뀐 경우: 조용히 0건이 되지 않고 실패로 드러나야 함
        assertFeedParseError(() -> parse(toInputStream("{\"vulnerabilities\":[{\"cve\":\"CVE-2026-1\"},{\"cve\":\"CVE-2026-2\"}]}"), null));
    }

    @Test
    void returnsEmptyListWhenCatalogHasNoEntries() {
        assertThat(parse(toInputStream("{\"vulnerabilities\":[]}"), null)).isEmpty();
    }

    @Test
    void throwsFeedParseErrorOnBrokenJson() {
        assertFeedParseError(() -> parse(toInputStream("{\"vulnerabilities\":["), null));
    }

    private static void assertFeedParseError(Runnable parsing) {
        assertThatThrownBy(parsing::run)
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FEED_PARSE_ERROR);
    }

    private static InputStream openFixture() {
        return KevCatalogParserTest.class.getResourceAsStream("/fixtures/kev/kev-catalog.json");
    }

    private static InputStream toInputStream(String json) {
        return new ByteArrayInputStream(json.getBytes(UTF_8));
    }
}
