package kr.suhsaechan.secbrief.advisory.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * RSS 1.0(RDF) 파싱 (Debian 보안 권고 모양, 직접 작성한 최소 fixture): 제목·링크, dc:date > 게시일 (날짜만이면 UTC 자정), 날짜 없으면 null
 */
class RssFeedParserRdfTest {

    private static final String PUBLISHER_NAME = "Debian 보안 권고";

    @Test
    void parsesRdfItemsWithTitleLinkAndPublisher() {
        List<CollectedArticle> articles = parseDebianFixture();

        assertThat(articles).hasSize(3);
        assertThat(articles.get(0).title()).isEqualTo("DSA-9001-1 example-server - security update");
        assertThat(articles.get(0).originalUrl()).isEqualTo("https://security-tracker.debian.org/tracker/DSA-9001-1");
        assertThat(articles).allSatisfy(article -> {
            assertThat(article.publisherName()).isEqualTo(PUBLISHER_NAME);
            assertThat(article.cveId()).isNull();
            assertThat(article.imageSourceUrl()).isNull();
        });
    }

    @Test
    void readsDateOnlyDcDateAsUtcMidnight() {
        assertThat(parseDebianFixture().get(0).publishedAt()).isEqualTo(Instant.parse("2026-11-22T00:00:00Z"));
    }

    @Test
    void readsDcDateWithOffsetToInstant() {
        // 2026-11-20T18:45:30+01:00
        assertThat(parseDebianFixture().get(1).publishedAt()).isEqualTo(Instant.parse("2026-11-20T17:45:30Z"));
    }

    @Test
    void returnsNullPublishedAtWhenItemHasNoDcDate() {
        // 채널의 dc:date 를 항목 게시일로 빌려 쓰지 않음 (정규화가 수집 시각으로 대체하고 추정 표시)
        assertThat(parseDebianFixture().get(2).publishedAt()).isNull();
    }

    private static List<CollectedArticle> parseDebianFixture() {
        InputStream fixtureStream = RssFeedParserRdfTest.class.getResourceAsStream("/fixtures/rss/debian-dsa.xml");
        return RssFeedParser.parse(fixtureStream, PUBLISHER_NAME);
    }
}
