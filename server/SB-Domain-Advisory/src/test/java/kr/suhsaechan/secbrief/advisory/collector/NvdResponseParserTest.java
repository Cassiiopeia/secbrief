package kr.suhsaechan.secbrief.advisory.collector;

import static java.nio.charset.StandardCharsets.UTF_8;
import static kr.suhsaechan.secbrief.common.json.JsonSupport.OBJECT_MAPPER;
import static kr.suhsaechan.secbrief.advisory.collector.NvdResponseParser.parse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * NVD 응답: 9.0 이상만, v3.1 우선 > v4.0, 설명 120자 제목, 오프셋 없는 게시일 = UTC
 */
class NvdResponseParserTest {

    @Test
    void keepsOnlyCriticalScoresPreferringV31() {
        NvdPage page = parse(openFixture());

        // D(v3.1 8.8 + v4.0 9.5)는 v3.1 우선이라 제외, A 는 Secondary 7.0 보다 Primary 9.8 우선
        assertThat(page.articles())
                .extracting(CollectedArticle::cveId, CollectedArticle::cvssScore, CollectedArticle::cvssVersion)
                .containsExactly(tuple("CVE-2026-0001", new BigDecimal("9.8"), "3.1"),
                        tuple("CVE-2026-0002", new BigDecimal("9.3"), "4.0"));
    }

    @Test
    void takesVulnerableCpeVendorProductsAsAffectedProductFact() throws Exception {
        // NVD configurations 실제 모양 (분석이 끝난 CVE): vulnerable=false 인 실행 환경 CPE 는 뺌, 중복 제거
        JsonNode configurations = OBJECT_MAPPER.readTree("""
                [{"nodes":[{"operator":"OR","cpeMatch":[
                  {"vulnerable":true,"criteria":"cpe:2.3:o:fortinet:fortios:*:*:*:*:*:*:*:*","versionEndExcluding":"7.4.5"},
                  {"vulnerable":true,"criteria":"cpe:2.3:o:fortinet:fortios:7.6.0:*:*:*:*:*:*:*"},
                  {"vulnerable":true,"criteria":"cpe:2.3:a:fortinet:forti_proxy:*:*:*:*:*:*:*:*"}]},
                 {"nodes":[{"cpeMatch":[{"vulnerable":false,"criteria":"cpe:2.3:h:example:appliance:-:*:*:*:*:*:*:*"}]}]}]}]""");

        assertThat(NvdResponseParser.toCpeVendorProducts(configurations))
                .containsExactly("fortinet fortios", "fortinet forti proxy");
        assertThat(NvdResponseParser.toCpeVendorProducts(OBJECT_MAPPER.readTree("[]"))).isEmpty();
    }

    @Test
    void takesFullEnglishDescriptionAsSourceText() {
        CollectedArticle article = parse(openFixture()).articles().get(0);

        assertThat(article.sourceText())
                .startsWith("A remote code execution vulnerability exists in Example Gateway request routing")
                .endsWith("allowing an unauthenticated attacker to run arbitrary commands.");
    }

    @Test
    void takesCvssAndWeaknessFactsFromChosenMetric() {
        List<CollectedArticle> articles = parse(openFixture()).articles();

        // Primary 항목의 값, CWE 는 중복·정보 없음 표시(NVD-CWE-noinfo) 제외
        assertThat(articles.get(0).sourceFacts()).containsExactly(
                new SourceFact("CVSS 점수", "9.8"),
                new SourceFact("CVSS 버전", "3.1"),
                new SourceFact("CVSS 벡터", "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"),
                new SourceFact("심각도", "CRITICAL"),
                new SourceFact("CWE", "CWE-94"));
        // weaknesses 가 없으면 CWE 를 싣지 않음
        assertThat(articles.get(1).sourceFacts()).extracting(SourceFact::label)
                .containsExactly("CVSS 점수", "CVSS 버전", "CVSS 벡터", "심각도");
    }

    @Test
    void truncatesDescriptionTitleTo120Characters() {
        String title = parse(openFixture()).articles().get(0).title();

        assertThat(title).startsWith("CVE-2026-0001: ").endsWith("…");
        assertThat(title).hasSize("CVE-2026-0001: ".length() + 120 + 1);
    }

    @Test
    void keepsShortDescriptionWithoutTruncationMark() {
        assertThat(parse(openFixture()).articles().get(1).title())
                .isEqualTo("CVE-2026-0002: Example Library 4.0 deserialization flaw.");
    }

    @Test
    void mapsLinkPublisherAndKevFlag() {
        CollectedArticle article = parse(openFixture()).articles().get(0);

        assertThat(article.originalUrl()).isEqualTo("https://nvd.nist.gov/vuln/detail/CVE-2026-0001");
        assertThat(article.publisherName()).isEqualTo("NVD");
        assertThat(article.isKev()).isFalse();
    }

    @Test
    void readsPagingNumbers() {
        NvdPage page = parse(openFixture());

        assertThat(page.totalResults()).isEqualTo(4);
        assertThat(page.hasNextPage()).isFalse();
    }

    @Test
    void parsesPublishedWithoutOffsetAsUtc() {
        assertThat(parse(openFixture()).articles().get(0).publishedAt())
                .isEqualTo(Instant.parse("2026-11-20T15:15:10.123Z"));
    }

    @Test
    void hasNextPageWhenMoreResultsRemain() {
        assertThat(new NvdPage(List.of(), 0, 2000, 2001).hasNextPage()).isTrue();
        assertThat(new NvdPage(List.of(), 2000, 1, 2001).hasNextPage()).isFalse();
    }

    @Test
    void throwsFeedParseErrorWhenVulnerabilitiesMissing() {
        assertThatThrownBy(() -> parse(toInputStream("{\"message\":\"x\"}")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FEED_PARSE_ERROR);
    }

    @Test
    void throwsFeedParseErrorOnBrokenJson() {
        assertThatThrownBy(() -> parse(toInputStream("<html>")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FEED_PARSE_ERROR);
    }

    private static InputStream openFixture() {
        return NvdResponseParserTest.class.getResourceAsStream("/fixtures/nvd/nvd-cves-page.json");
    }

    private static InputStream toInputStream(String json) {
        return new ByteArrayInputStream(json.getBytes(UTF_8));
    }
}
