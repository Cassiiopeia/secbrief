package kr.suhsaechan.secbrief.advisory.collector;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import org.junit.jupiter.api.Test;

/**
 * KEV 수집기: 출처 경로로 카탈로그를 받아 since 이후 등재분만 반환
 */
class KevArticleCollectorTest {

    private final FeedHttpClient feedHttpClient = mock(FeedHttpClient.class);
    private final KevArticleCollector collector = new KevArticleCollector(feedHttpClient);
    private final Source source = createSource(SourceCode.CISA_KEV, ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, FeedFormat.CISA_KEV_JSON, null);

    @Test
    void supportsCisaKevJsonFormat() {
        assertThat(collector.supportedFeedFormat()).isEqualTo(FeedFormat.CISA_KEV_JSON);
    }

    @Test
    void collectsEntriesAddedSinceFromFetchedCatalog() throws IOException {
        byte[] catalogBytes = KevArticleCollectorTest.class.getResourceAsStream("/fixtures/kev/kev-catalog.json").readAllBytes();
        when(feedHttpClient.fetch(source.getFeedUrl())).thenReturn(catalogBytes);

        List<CollectedArticle> articles = collector.collect(source, Instant.parse("2026-11-15T00:00:00Z"));

        assertThat(articles).extracting(CollectedArticle::cveId).containsExactly("CVE-2026-1111");
    }
}
