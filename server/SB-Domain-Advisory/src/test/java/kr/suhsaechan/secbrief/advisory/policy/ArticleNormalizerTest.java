package kr.suhsaechan.secbrief.advisory.policy;

import static kr.suhsaechan.secbrief.advisory.policy.ArticleNormalizer.normalize;
import static kr.suhsaechan.secbrief.advisory.policy.ArticleNormalizer.toSanitizedErrorMessage;
import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.collector.CollectedArticle;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;
import org.junit.jupiter.api.Test;

/**
 * 정규화: 제목·링크 정리, 게시일 보정(없음·미래), 출처 값 적용, 오류 메시지 정리, 이미지 주소 정리(불량이면 이미지 없이)
 */
class ArticleNormalizerTest {

    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");

    private final Source kisaSource = createSource(SourceCode.KISA, ArticleKind.ADVISORY, ArticleRegion.DOMESTIC, FeedFormat.RSS, null);

    @Test
    void cleansSourceTextAndFactsIntoDraft() {
        CollectedArticle collected = new CollectedArticle("Example VPN flaw exploited", "https://a.kr/1", null, NOW,
                null, null, null, false, null,
                "<p>Attackers are exploiting a flaw in <b>Example VPN</b> &amp; Example Gateway to steal session tokens.</p>",
                List.of(new SourceFact(" 조치 기한 ", " 2026-12-11\n"), new SourceFact("조치 요구", " ")));

        ArticleDraft draft = normalize(collected, kisaSource, NOW).orElseThrow();

        assertThat(draft.sourceText())
                .isEqualTo("Attackers are exploiting a flaw in Example VPN & Example Gateway to steal session tokens.");
        assertThat(draft.sourceFacts()).containsExactly(new SourceFact("조치 기한", "2026-12-11"));
    }

    @Test
    void leavesSourceTextNullWhenItAddsNothingToTitle() {
        String title = "CVE-2026-0002: Example Library 4.0 deserialization flaw in the request parser.";
        CollectedArticle sameAsTitle = new CollectedArticle(title, "https://a.kr/1", null, NOW, null, null, null, false,
                null, "Example Library 4.0 deserialization flaw in the request parser.", List.of());
        CollectedArticle tooShort = new CollectedArticle("제목", "https://a.kr/2", null, NOW, null, null, null, false,
                null, "<p>짧은 요약</p>", List.of());

        assertThat(normalize(sameAsTitle, kisaSource, NOW).orElseThrow().sourceText()).isNull();
        assertThat(normalize(tooShort, kisaSource, NOW).orElseThrow().sourceText()).isNull();
        assertThat(normalize(createCollectedArticle("제목", "https://a.kr/3", NOW), kisaSource, NOW).orElseThrow().sourceText())
                .isNull();
    }

    @Test
    void usesCollectedAtWhenPublishedAtMissing() {
        ArticleDraft draft = normalize(createCollectedArticle("제목", "https://a.kr/1", null), kisaSource, NOW).orElseThrow();

        assertThat(draft.publishedAt()).isEqualTo(NOW);
        assertThat(draft.isPublishedAtEstimated()).isTrue();
    }

    @Test
    void clampsFuturePublishedAtToNow() {
        ArticleDraft draft = normalize(createCollectedArticle("제목", "https://a.kr/1", NOW.plus(Duration.ofHours(9))), kisaSource, NOW).orElseThrow();

        assertThat(draft.publishedAt()).isEqualTo(NOW);
        assertThat(draft.isPublishedAtEstimated()).isTrue();
    }

    @Test
    void keepsPublishedAtWithinTolerance() {
        Instant slightlyFuture = NOW.plus(Duration.ofMinutes(30));

        ArticleDraft draft = normalize(createCollectedArticle("제목", "https://a.kr/1", slightlyFuture), kisaSource, NOW).orElseThrow();

        assertThat(draft.publishedAt()).isEqualTo(slightlyFuture);
        assertThat(draft.isPublishedAtEstimated()).isFalse();
    }

    @Test
    void keepsPastPublishedAt() {
        Instant yesterday = NOW.minus(Duration.ofDays(1));

        ArticleDraft draft = normalize(createCollectedArticle("제목", "https://a.kr/1", yesterday), kisaSource, NOW).orElseThrow();

        assertThat(draft.publishedAt()).isEqualTo(yesterday);
        assertThat(draft.isPublishedAtEstimated()).isFalse();
    }

    @Test
    void stripsHtmlAndCollapsesWhitespaceInTitle() {
        Optional<ArticleDraft> draft = normalize(createCollectedArticle("  <b>Ivanti</b>\n  긴급 패치 ", "https://a.kr/1", NOW), kisaSource, NOW);

        assertThat(draft.orElseThrow().title()).isEqualTo("Ivanti 긴급 패치");
    }

    @Test
    void unescapesHtmlCharacterReferencesInTitle() {
        // KISA 피드는 따옴표·기호를 문자 참조로 줌
        Optional<ArticleDraft> draft = normalize(createCollectedArticle(
                "사이버위기 경보 단계 &#39;관심&#39; → &#39;주의&#39; 상향 조정 &amp; 안내", "https://a.kr/2", NOW), kisaSource, NOW);

        assertThat(draft.orElseThrow().title()).isEqualTo("사이버위기 경보 단계 '관심' → '주의' 상향 조정 & 안내");
    }

    @Test
    void unescapesZeroPaddedNumericReferenceInTitle() {
        // 기사 800: KISA 경보단계 피드가 &#039; 로 줌 (#96)
        Optional<ArticleDraft> draft = normalize(createCollectedArticle(
                "국내·외 사이버 위협 우려가 고조됨에 따라 &#039;주의&#039; 경보 발령", "https://a.kr/3", NOW), kisaSource, NOW);

        assertThat(draft.orElseThrow().title()).isEqualTo("국내·외 사이버 위협 우려가 고조됨에 따라 '주의' 경보 발령");
    }

    @Test
    void oldRaisedAlertLevelArticleIsNotUrgentWhenFirstCollected() {
        Source alertLevelSource = createSource(SourceCode.KISA_ALERT_LEVEL, ArticleKind.ADVISORY, ArticleRegion.DOMESTIC,
                FeedFormat.RSS, null);
        String raisedTitle = "사이버위기 경보 단계 '관심' → '주의' 상향 조정";

        // 지난 글 채우기로 들어온 예전 상향 공지는 긴급 아님, 갓 올라온 상향 공지는 긴급
        assertThat(normalize(createCollectedArticle(raisedTitle, "https://a.kr/4", NOW.minus(Duration.ofDays(40))),
                alertLevelSource, NOW).orElseThrow().isUrgent()).isFalse();
        assertThat(normalize(createCollectedArticle(raisedTitle, "https://a.kr/5", NOW.minus(Duration.ofHours(3))),
                alertLevelSource, NOW).orElseThrow().isUrgent()).isTrue();
    }

    @Test
    void collapsesNonBreakingSpaceInTitle() {
        ArticleDraft draft = normalize(createCollectedArticle("\u00A0Ivanti\u00A0\u00A0패치\u00A0", "https://a.kr/1", NOW), kisaSource, NOW).orElseThrow();

        assertThat(draft.title()).isEqualTo("Ivanti 패치");
    }

    @Test
    void limitsTitleToFiveHundredCharacters() {
        ArticleDraft draft = normalize(createCollectedArticle("가".repeat(600), "https://a.kr/1", NOW), kisaSource, NOW).orElseThrow();

        assertThat(draft.title()).hasSize(500);
    }

    @Test
    void doesNotSplitSurrogatePairWhenLimitingTitle() {
        // 499자 + 이모지(2칸): 500칸에서 자르면 앞 절반만 남는 위치
        String title = "a".repeat(499) + "😀";

        ArticleDraft draft = normalize(createCollectedArticle(title, "https://a.kr/1", NOW), kisaSource, NOW).orElseThrow();

        assertThat(draft.title()).isEqualTo("a".repeat(499));
    }

    @Test
    void dropsFragmentFromOriginalUrl() {
        ArticleDraft draft = normalize(createCollectedArticle("제목", "  https://a.kr/1#top ", NOW), kisaSource, NOW).orElseThrow();

        assertThat(draft.originalUrl()).isEqualTo("https://a.kr/1");
    }

    @Test
    void rejectsBlankTitleOrNonHttpUrl() {
        assertThat(normalize(createCollectedArticle("<br>", "https://a.kr/1", NOW), kisaSource, NOW)).isEmpty();
        assertThat(normalize(createCollectedArticle(null, "https://a.kr/1", NOW), kisaSource, NOW)).isEmpty();
        assertThat(normalize(createCollectedArticle("제목", "javascript:alert(1)", NOW), kisaSource, NOW)).isEmpty();
        assertThat(normalize(createCollectedArticle("제목", null, NOW), kisaSource, NOW)).isEmpty();
        assertThat(normalize(createCollectedArticle("제목", "#top", NOW), kisaSource, NOW)).isEmpty();
    }

    @Test
    void rejectsUrlLongerThanColumnLimit() {
        String tooLongUrl = "https://a.kr/" + "x".repeat(1000);

        assertThat(normalize(createCollectedArticle("제목", tooLongUrl, NOW), kisaSource, NOW)).isEmpty();
    }

    @Test
    void appliesSourceKindRegionAndUrgency() {
        ArticleDraft draft = normalize(createCollectedArticle("[긴급] Ivanti 보안 업데이트 권고", "https://a.kr/2", NOW), kisaSource, NOW).orElseThrow();

        assertThat(draft.kind()).isEqualTo(ArticleKind.ADVISORY);
        assertThat(draft.region()).isEqualTo(ArticleRegion.DOMESTIC);
        assertThat(draft.isUrgent()).isTrue();
    }

    @Test
    void marksKevAndHighCvssUrgentAndCarriesCveFields() {
        Source nvdSource = createSource(SourceCode.NVD, ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, FeedFormat.NVD_API, null);
        CollectedArticle collected = new CollectedArticle("CVE-2026-0001 원격 코드 실행", "https://nvd.example/CVE-2026-0001",
                "NVD", NOW, "CVE-2026-0001", new BigDecimal("9.8"), "3.1", false);

        ArticleDraft draft = normalize(collected, nvdSource, NOW).orElseThrow();

        assertThat(draft.isUrgent()).isTrue();
        assertThat(draft.cveId()).isEqualTo("CVE-2026-0001");
        assertThat(draft.cvssScore()).isEqualByComparingTo("9.8");
        assertThat(draft.cvssVersion()).isEqualTo("3.1");
        assertThat(draft.kind()).isEqualTo(ArticleKind.VULNERABILITY);
        assertThat(draft.region()).isEqualTo(ArticleRegion.OVERSEAS);
    }

    @Test
    void usesSourceDisplayNameWhenPublisherBlank() {
        ArticleDraft draft = normalize(createCollectedArticleWithPublisher("  ", "제목"), kisaSource, NOW).orElseThrow();

        assertThat(draft.publisherName()).isEqualTo(kisaSource.getDisplayName());
    }

    @Test
    void keepsCollectedPublisherWhenPresent() {
        ArticleDraft draft = normalize(createCollectedArticleWithPublisher(" 보호나라 ", "제목"), kisaSource, NOW).orElseThrow();

        assertThat(draft.publisherName()).isEqualTo("보호나라");
    }

    @Test
    void limitsPublisherNameToColumnLength() {
        ArticleDraft draft = normalize(createCollectedArticleWithPublisher("p".repeat(150), "제목"), kisaSource, NOW).orElseThrow();

        assertThat(draft.publisherName()).hasSize(100);
    }

    @Test
    void sanitizesControlCharactersAndLimitsLength() {
        assertThat(toSanitizedErrorMessage("a\nb\u0000c")).isEqualTo("a?b?c");
        assertThat(toSanitizedErrorMessage("x".repeat(600))).hasSize(500);
        assertThat(toSanitizedErrorMessage(null)).isEqualTo("unknown");
    }

    private static CollectedArticle createCollectedArticle(String title, String originalUrl, Instant publishedAt) {
        return new CollectedArticle(title, originalUrl, "게시자", publishedAt, null, null, null, false);
    }

    @Test
    void keepsHttpImageUrlTrimmed() {
        ArticleDraft draft = normalize(createCollectedArticleWithImage(" https://images.example.test/a.jpg?w=600 "), kisaSource, NOW).orElseThrow();

        assertThat(draft.imageSourceUrl()).isEqualTo("https://images.example.test/a.jpg?w=600");
    }

    @Test
    void dropsOnlyImageWhenImageUrlIsNotHttp() {
        for (String badImageUrl : new String[] {"data:image/png;base64,AAAA", "file:///etc/passwd", "//images.example.test/a.jpg",
                "/relative/a.jpg", "javascript:alert(1)", "https://", "https://images.example.test/a b.jpg", ""}) {
            Optional<ArticleDraft> draft = normalize(createCollectedArticleWithImage(badImageUrl), kisaSource, NOW);

            assertThat(draft).as(badImageUrl).isPresent();
            assertThat(draft.get().imageSourceUrl()).as(badImageUrl).isNull();
        }
    }

    @Test
    void dropsImageUrlLongerThanColumn() {
        String prefix = "https://images.example.test/";
        String longestImageUrl = prefix + "a".repeat(1000 - prefix.length());

        assertThat(normalize(createCollectedArticleWithImage(longestImageUrl), kisaSource, NOW).orElseThrow().imageSourceUrl())
                .isEqualTo(longestImageUrl);
        assertThat(normalize(createCollectedArticleWithImage(longestImageUrl + "a"), kisaSource, NOW).orElseThrow().imageSourceUrl())
                .isNull();
    }

    @Test
    void hasNoImageWhenSourceGivesNone() {
        assertThat(normalize(createCollectedArticle("제목", "https://a.kr/1", NOW), kisaSource, NOW).orElseThrow().imageSourceUrl())
                .isNull();
    }

    private static CollectedArticle createCollectedArticleWithImage(String imageSourceUrl) {
        return new CollectedArticle("제목", "https://a.kr/1", "게시자", NOW, null, null, null, false, imageSourceUrl);
    }

    private static CollectedArticle createCollectedArticleWithPublisher(String publisherName, String title) {
        return new CollectedArticle(title, "https://a.kr/1", publisherName, NOW, null, null, null, false);
    }
}
