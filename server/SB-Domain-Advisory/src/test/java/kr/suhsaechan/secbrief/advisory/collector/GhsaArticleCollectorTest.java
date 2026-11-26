package kr.suhsaechan.secbrief.advisory.collector;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import org.junit.jupiter.api.Test;

/**
 * GHSA 수집기: 페이지 쿼리·GitHub 헤더로 요청, since 이후분만
 */
class GhsaArticleCollectorTest {

    private final FeedHttpClient feedHttpClient = mock(FeedHttpClient.class);
    private final GhsaArticleCollector collector = new GhsaArticleCollector(feedHttpClient);
    private final Source source = createSource(SourceCode.GHSA, ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, FeedFormat.GHSA_API, null);

    @Test
    void requestsNewestPageWithGithubHeadersAndKeepsSinceOnly() throws IOException {
        byte[] responseBytes = GhsaArticleCollectorTest.class.getResourceAsStream("/fixtures/ghsa/ghsa-advisories.json").readAllBytes();
        String expectedUrl = source.getFeedUrl() + "&per_page=100&sort=published&direction=desc";
        when(feedHttpClient.fetch(eq(expectedUrl), anyMap())).thenReturn(responseBytes);

        CollectOutcome outcome = collector.collect(source, Instant.parse("2026-11-21T00:00:00Z"));

        assertThat(outcome.isNotModified()).isFalse();
        assertThat(outcome.feedValidators()).isEqualTo(FeedValidators.NONE);
        assertThat(outcome.articles()).extracting(CollectedArticle::cveId).containsExactly("CVE-2026-5555");
        verify(feedHttpClient).fetch(expectedUrl, Map.of(
                "Accept", "application/vnd.github+json",
                "X-GitHub-Api-Version", "2022-11-28"));
    }
}
