package kr.suhsaechan.secbrief.advisory.collector;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import org.junit.jupiter.api.Test;

/**
 * KEV 수집기: 출처 경로로 카탈로그를 조건부로 받아 since 이후 등재분만 반환, 304 면 변경 없음
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
        FeedValidators newValidators = new FeedValidators("\"kev-v2\"", null);
        when(feedHttpClient.fetchIfChanged(source.getFeedUrl(), FeedValidators.NONE))
                .thenReturn(FeedFetchResult.modified(catalogBytes, newValidators));

        CollectOutcome outcome = collector.collect(source, Instant.parse("2026-11-15T00:00:00Z"));

        assertThat(outcome.articles()).extracting(CollectedArticle::cveId).containsExactly("CVE-2026-1111");
        assertThat(outcome.feedValidators()).isEqualTo(newValidators);
    }

    @Test
    void returnsNotModifiedWhenCatalogIsUnchanged() {
        FeedValidators storedValidators = new FeedValidators("\"kev-v1\"", "Wed, 25 Nov 2026 00:00:00 GMT");
        source.replaceFeedValidators(storedValidators.etag(), storedValidators.lastModified());
        when(feedHttpClient.fetchIfChanged(source.getFeedUrl(), storedValidators))
                .thenReturn(FeedFetchResult.notModified(storedValidators));

        CollectOutcome outcome = collector.collect(source, Instant.parse("2026-11-15T00:00:00Z"));

        assertThat(outcome.isNotModified()).isTrue();
        assertThat(outcome.articles()).isEmpty();
    }
}
