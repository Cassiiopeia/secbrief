package kr.suhsaechan.secbrief.advisory.collector;

import static java.nio.charset.StandardCharsets.UTF_8;
import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.source;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
 * RSS 수집기: 출처 경로로 내려받아 파싱, 게시자는 출처 표시 이름, since 무시
 */
class RssArticleCollectorTest {

    private static final String FEED_XML = """
            <rss version="2.0"><channel><title>t</title>
            <item><title>제목 하나</title><link>https://a.example.test/1</link><pubDate>Sun, 22 Nov 2026 13:45:37 -0500</pubDate></item>
            </channel></rss>""";

    private final FeedHttpClient feedHttpClient = mock(FeedHttpClient.class);
    private final RssArticleCollector collector = new RssArticleCollector(feedHttpClient);
    private final Source source = source(SourceCode.DAILYSECU, ArticleKind.NEWS, ArticleRegion.DOMESTIC, FeedFormat.RSS, null);

    @Test
    void supportsRssFormat() {
        assertThat(collector.supportedFeedFormat()).isEqualTo(FeedFormat.RSS);
    }

    @Test
    void collectsWithSourceDisplayNameAsPublisherAndIgnoresSince() {
        when(feedHttpClient.fetch(source.getFeedUrl())).thenReturn(FEED_XML.getBytes(UTF_8));

        List<CollectedArticle> articles = collector.collect(source, Instant.parse("2030-01-01T00:00:00Z"));

        assertThat(articles).hasSize(1);
        assertThat(articles.get(0).publisherName()).isEqualTo(source.getDisplayName());
        assertThat(articles.get(0).title()).isEqualTo("제목 하나");
    }
}
