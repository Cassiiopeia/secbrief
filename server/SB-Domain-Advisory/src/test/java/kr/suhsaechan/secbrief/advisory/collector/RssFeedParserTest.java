package kr.suhsaechan.secbrief.advisory.collector;

import static java.nio.charset.StandardCharsets.UTF_8;
import static kr.suhsaechan.secbrief.advisory.collector.RssFeedParser.parse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * RSS 파싱: 실제 피드 4종(앞 2건만 남긴 fixture)의 제목·링크·게시자·게시일 매핑, 인코딩, 깨진 XML, 대표 이미지 주소(첨부 > Media RSS > 요약 img)
 * 요약 입력: 요약문(description) 우선 > 본문 칸(content), 요약문을 안 주는 피드(KISA)는 없음, 사실 값은 항상 없음
 */
class RssFeedParserTest {

    @ParameterizedTest
    @ValueSource(strings = {"kisa-notice", "dailysecu", "the-hacker-news", "bleeping-computer"})
    void parsesTwoItemsWithTitleAndHttpLink(String fixtureName) {
        List<CollectedArticle> articles = parse(openFixture(fixtureName), "게시자");

        assertThat(articles).hasSize(2).allSatisfy(article -> {
            assertThat(article.title()).isNotBlank();
            assertThat(article.originalUrl()).startsWith("http");
            assertThat(article.publisherName()).isEqualTo("게시자");
            assertThat(article.cveId()).isNull();
            assertThat(article.isKev()).isFalse();
        });
    }

    @Test
    void takesSourceTextFromDescriptionThenContent() {
        List<CollectedArticle> articles = parse(openFixture("summary-variants"), "게시자");

        // HTML 은 그대로 넘김 (태그·문자 표기 정리는 정규화 단계)
        assertThat(articles.get(0).sourceText()).contains("exploiting a flaw in <b>Example VPN</b>")
                .doesNotContain("Full body");
        assertThat(articles.get(1).sourceText()).contains("Example Corp confirmed that an intruder");
        assertThat(articles.get(2).sourceText()).isNull();
        assertThat(articles).allSatisfy(article -> assertThat(article.sourceFacts()).isEmpty());
    }

    @Test
    void leavesSourceTextNullWhenFeedGivesNoSummary() {
        assertThat(parse(openFixture("kisa-notice"), "KISA"))
                .allSatisfy(article -> assertThat(article.sourceText()).isNull());
    }

    @Test
    void returnsNullPublishedAtWhenItemHasNoDate() {
        assertThat(parse(openFixture("kisa-notice"), "KISA").get(1).publishedAt()).isNull();
    }

    @Test
    void decodesKoreanTitleFromKisaFeed() {
        assertThat(parse(openFixture("kisa-notice"), "KISA").get(0).title()).containsPattern("[가-힣]");
    }

    @Test
    void trimsWhitespaceAroundCdataTitleAndLink() {
        CollectedArticle article = parse(openFixture("kisa-notice"), "KISA").get(0);

        assertThat(article.title()).isEqualTo(article.title().strip());
        assertThat(article.originalUrl()).isEqualTo(article.originalUrl().strip());
    }

    @Test
    void decodesXmlEntitiesInTitle() {
        String title = parse(openFixture("dailysecu"), "데일리시큐").get(0).title();

        assertThat(title).contains("\"중국 해커").doesNotContain("&quot;");
    }

    @Test
    void parsesRfc822DateWithOffsetToInstant() {
        // Sun, 22 Nov 2026 10:36:55 +0530
        assertThat(parse(openFixture("the-hacker-news"), "The Hacker News").get(0).publishedAt())
                .isEqualTo(Instant.parse("2026-11-22T05:06:55Z"));
    }

    @Test
    void readsDateOnlyPubDateAsSeoulMidnight() {
        // KISA: 2026-11-23 (시간대·시각 없음) > 서울 자정
        assertThat(parse(openFixture("kisa-notice"), "KISA").get(0).publishedAt())
                .isEqualTo(Instant.parse("2026-11-22T15:00:00Z"));
    }

    @Test
    void readsZonelessDateTimePubDateAsSeoulTime() {
        // 데일리시큐: 2026-11-23 14:12:17 (시간대 없음) > 서울 시각
        assertThat(parse(openFixture("dailysecu"), "데일리시큐").get(0).publishedAt())
                .isEqualTo(Instant.parse("2026-11-23T05:12:17Z"));
    }

    @Test
    void usesDisplayNameAsPublisherInsteadOfFeedAuthor() {
        assertThat(parse(openFixture("dailysecu"), "데일리시큐").get(0).publisherName()).isEqualTo("데일리시큐");
    }

    @Test
    void throwsFeedParseErrorOnBrokenXml() {
        assertThatThrownBy(() -> parse(new ByteArrayInputStream("<rss><".getBytes(UTF_8)), "x"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FEED_PARSE_ERROR);
    }

    @Test
    void prefersEnclosureImageOverMediaAndSummaryImage() {
        assertThat(parseImageVariants().get(0).imageSourceUrl()).isEqualTo("https://images.example.test/enclosure.jpg");
    }

    @Test
    void readsMediaThumbnailWhenEnclosureIsNotImage() {
        assertThat(parseImageVariants().get(1).imageSourceUrl()).isEqualTo("https://images.example.test/thumbnail.jpg");
    }

    @Test
    void readsImageMediaContentSkippingVideo() {
        assertThat(parseImageVariants().get(2).imageSourceUrl()).isEqualTo("https://images.example.test/content.png");
    }

    @Test
    void readsFirstImageTagInSummaryAndUnescapesAmpersand() {
        assertThat(parseImageVariants().get(3).imageSourceUrl())
                .isEqualTo("https://images.example.test/summary.jpg?w=600&h=400");
    }

    @Test
    void returnsNullImageWhenItemHasNoImage() {
        assertThat(parseImageVariants().get(4).imageSourceUrl()).isNull();
        // 실제 피드 fixture 는 이미지 요소를 지운 것이라 전부 없음
        assertThat(parse(openFixture("kisa-notice"), "KISA")).allSatisfy(
                article -> assertThat(article.imageSourceUrl()).isNull());
    }

    private static List<CollectedArticle> parseImageVariants() {
        return parse(openFixture("image-variants"), "게시자");
    }

    private static InputStream openFixture(String fixtureName) {
        return RssFeedParserTest.class.getResourceAsStream("/fixtures/rss/" + fixtureName + ".xml");
    }
}
