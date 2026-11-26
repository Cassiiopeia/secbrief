package kr.suhsaechan.secbrief.advisory.collector;

import static java.nio.charset.StandardCharsets.UTF_8;
import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import org.junit.jupiter.api.Test;

/**
 * RSS 수집기: 출처 경로로 조건부로 내려받아 파싱, 게시자는 출처 표시 이름, since 무시, 304 면 변경 없음
 */
class RssArticleCollectorTest {

    private static final String FEED_XML = """
            <rss version="2.0"><channel><title>t</title>
            <item><title>제목 하나</title><link>https://a.example.test/1</link><pubDate>Sun, 22 Nov 2026 13:45:37 -0500</pubDate></item>
            </channel></rss>""";

    private final FeedHttpClient feedHttpClient = mock(FeedHttpClient.class);
    private final RssArticleCollector collector = new RssArticleCollector(feedHttpClient);
    private final Source source = createSource(SourceCode.DAILYSECU, ArticleKind.NEWS, ArticleRegion.DOMESTIC, FeedFormat.RSS, null);

    @Test
    void supportsRssFormat() {
        assertThat(collector.supportedFeedFormat()).isEqualTo(FeedFormat.RSS);
    }

    @Test
    void collectsWithSourceDisplayNameAsPublisherAndIgnoresSince() {
        when(feedHttpClient.fetchIfChanged(eq(source.getFeedUrl()), any()))
                .thenReturn(FeedFetchResult.modified(FEED_XML.getBytes(UTF_8), FeedValidators.NONE));

        List<CollectedArticle> articles = collector.collect(source, Instant.parse("2030-01-01T00:00:00Z")).articles();

        assertThat(articles).hasSize(1);
        assertThat(articles.get(0).publisherName()).isEqualTo(source.getDisplayName());
        assertThat(articles.get(0).title()).isEqualTo("제목 하나");
    }

    @Test
    void sendsStoredValidatorsAndReturnsNewValidatorsWithArticles() {
        source.replaceFeedValidators("\"old\"", "Tue, 24 Nov 2026 00:00:00 GMT");
        FeedValidators newValidators = new FeedValidators("\"new\"", "Wed, 25 Nov 2026 00:00:00 GMT");
        when(feedHttpClient.fetchIfChanged(eq(source.getFeedUrl()), any()))
                .thenReturn(FeedFetchResult.modified(FEED_XML.getBytes(UTF_8), newValidators));

        CollectOutcome outcome = collector.collect(source, null);

        verify(feedHttpClient).fetchIfChanged(source.getFeedUrl(),
                new FeedValidators("\"old\"", "Tue, 24 Nov 2026 00:00:00 GMT"));
        assertThat(outcome.isNotModified()).isFalse();
        assertThat(outcome.feedValidators()).isEqualTo(newValidators);
        assertThat(outcome.articles()).hasSize(1);
    }

    @Test
    void returnsNotModifiedWithoutParsingWhenFeedIsUnchanged() {
        FeedValidators storedValidators = new FeedValidators("\"old\"", null);
        source.replaceFeedValidators(storedValidators.etag(), storedValidators.lastModified());
        when(feedHttpClient.fetchIfChanged(source.getFeedUrl(), storedValidators))
                .thenReturn(FeedFetchResult.notModified(storedValidators));

        CollectOutcome outcome = collector.collect(source, null);

        assertThat(outcome.isNotModified()).isTrue();
        assertThat(outcome.feedValidators()).isEqualTo(storedValidators);
        assertThat(outcome.articles()).isEmpty();
    }
}
