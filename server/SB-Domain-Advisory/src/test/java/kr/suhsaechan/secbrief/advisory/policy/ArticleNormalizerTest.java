package kr.suhsaechan.secbrief.advisory.policy;

import static kr.suhsaechan.secbrief.advisory.policy.ArticleNormalizer.normalize;
import static kr.suhsaechan.secbrief.advisory.policy.ArticleNormalizer.toSanitizedErrorMessage;
import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.source;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.collector.CollectedArticle;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import org.junit.jupiter.api.Test;

/**
 * 정규화: 제목·링크 정리, 게시일 보정(없음·미래), 출처 값 적용, 오류 메시지 정리
 */
class ArticleNormalizerTest {

    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");

    private final Source kisaSource = source(SourceCode.KISA, ArticleKind.ADVISORY, ArticleRegion.DOMESTIC, FeedFormat.RSS, null);

    @Test
    void usesCollectedAtWhenPublishedAtMissing() {
        ArticleDraft draft = normalize(collected("제목", "https://a.kr/1", null), kisaSource, NOW).orElseThrow();

        assertThat(draft.publishedAt()).isEqualTo(NOW);
        assertThat(draft.isPublishedAtEstimated()).isTrue();
    }

    @Test
    void clampsFuturePublishedAtToNow() {
        ArticleDraft draft = normalize(collected("제목", "https://a.kr/1", NOW.plus(Duration.ofHours(9))), kisaSource, NOW).orElseThrow();

        assertThat(draft.publishedAt()).isEqualTo(NOW);
        assertThat(draft.isPublishedAtEstimated()).isTrue();
    }

    @Test
    void keepsPublishedAtWithinTolerance() {
        Instant slightlyFuture = NOW.plus(Duration.ofMinutes(30));

        ArticleDraft draft = normalize(collected("제목", "https://a.kr/1", slightlyFuture), kisaSource, NOW).orElseThrow();

        assertThat(draft.publishedAt()).isEqualTo(slightlyFuture);
        assertThat(draft.isPublishedAtEstimated()).isFalse();
    }

    @Test
    void keepsPastPublishedAt() {
        Instant yesterday = NOW.minus(Duration.ofDays(1));

        ArticleDraft draft = normalize(collected("제목", "https://a.kr/1", yesterday), kisaSource, NOW).orElseThrow();

        assertThat(draft.publishedAt()).isEqualTo(yesterday);
        assertThat(draft.isPublishedAtEstimated()).isFalse();
    }

    @Test
    void stripsHtmlAndCollapsesWhitespaceInTitle() {
        Optional<ArticleDraft> draft = normalize(collected("  <b>Ivanti</b>\n  긴급 패치 ", "https://a.kr/1", NOW), kisaSource, NOW);

        assertThat(draft.orElseThrow().title()).isEqualTo("Ivanti 긴급 패치");
    }

    @Test
    void collapsesNonBreakingSpaceInTitle() {
        ArticleDraft draft = normalize(collected("\u00A0Ivanti\u00A0\u00A0패치\u00A0", "https://a.kr/1", NOW), kisaSource, NOW).orElseThrow();

        assertThat(draft.title()).isEqualTo("Ivanti 패치");
    }

    @Test
    void limitsTitleToFiveHundredCharacters() {
        ArticleDraft draft = normalize(collected("가".repeat(600), "https://a.kr/1", NOW), kisaSource, NOW).orElseThrow();

        assertThat(draft.title()).hasSize(500);
    }

    @Test
    void doesNotSplitSurrogatePairWhenLimitingTitle() {
        // 499자 + 이모지(2칸): 500칸에서 자르면 앞 절반만 남는 위치
        String title = "a".repeat(499) + "😀";

        ArticleDraft draft = normalize(collected(title, "https://a.kr/1", NOW), kisaSource, NOW).orElseThrow();

        assertThat(draft.title()).isEqualTo("a".repeat(499));
    }

    @Test
    void dropsFragmentFromOriginalUrl() {
        ArticleDraft draft = normalize(collected("제목", "  https://a.kr/1#top ", NOW), kisaSource, NOW).orElseThrow();

        assertThat(draft.originalUrl()).isEqualTo("https://a.kr/1");
    }

    @Test
    void rejectsBlankTitleOrNonHttpUrl() {
        assertThat(normalize(collected("<br>", "https://a.kr/1", NOW), kisaSource, NOW)).isEmpty();
        assertThat(normalize(collected(null, "https://a.kr/1", NOW), kisaSource, NOW)).isEmpty();
        assertThat(normalize(collected("제목", "javascript:alert(1)", NOW), kisaSource, NOW)).isEmpty();
        assertThat(normalize(collected("제목", null, NOW), kisaSource, NOW)).isEmpty();
        assertThat(normalize(collected("제목", "#top", NOW), kisaSource, NOW)).isEmpty();
    }

    @Test
    void rejectsUrlLongerThanColumnLimit() {
        String tooLongUrl = "https://a.kr/" + "x".repeat(1000);

        assertThat(normalize(collected("제목", tooLongUrl, NOW), kisaSource, NOW)).isEmpty();
    }

    @Test
    void appliesSourceKindRegionAndUrgency() {
        ArticleDraft draft = normalize(collected("[긴급] Ivanti 보안 업데이트 권고", "https://a.kr/2", NOW), kisaSource, NOW).orElseThrow();

        assertThat(draft.kind()).isEqualTo(ArticleKind.ADVISORY);
        assertThat(draft.region()).isEqualTo(ArticleRegion.DOMESTIC);
        assertThat(draft.isUrgent()).isTrue();
    }

    @Test
    void marksKevAndHighCvssUrgentAndCarriesCveFields() {
        Source nvdSource = source(SourceCode.NVD, ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, FeedFormat.NVD_API, null);
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
        ArticleDraft draft = normalize(collectedWithPublisher("  ", "제목"), kisaSource, NOW).orElseThrow();

        assertThat(draft.publisherName()).isEqualTo(kisaSource.getDisplayName());
    }

    @Test
    void keepsCollectedPublisherWhenPresent() {
        ArticleDraft draft = normalize(collectedWithPublisher(" 보호나라 ", "제목"), kisaSource, NOW).orElseThrow();

        assertThat(draft.publisherName()).isEqualTo("보호나라");
    }

    @Test
    void limitsPublisherNameToColumnLength() {
        ArticleDraft draft = normalize(collectedWithPublisher("p".repeat(150), "제목"), kisaSource, NOW).orElseThrow();

        assertThat(draft.publisherName()).hasSize(100);
    }

    @Test
    void sanitizesControlCharactersAndLimitsLength() {
        assertThat(toSanitizedErrorMessage("a\nb\u0000c")).isEqualTo("a?b?c");
        assertThat(toSanitizedErrorMessage("x".repeat(600))).hasSize(500);
        assertThat(toSanitizedErrorMessage(null)).isEqualTo("unknown");
    }

    private static CollectedArticle collected(String title, String originalUrl, Instant publishedAt) {
        return new CollectedArticle(title, originalUrl, "게시자", publishedAt, null, null, null, false);
    }

    private static CollectedArticle collectedWithPublisher(String publisherName, String title) {
        return new CollectedArticle(title, "https://a.kr/1", publisherName, NOW, null, null, null, false);
    }
}
