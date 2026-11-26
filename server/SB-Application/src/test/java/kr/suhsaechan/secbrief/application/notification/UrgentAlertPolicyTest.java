package kr.suhsaechan.secbrief.application.notification;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import org.junit.jupiter.api.Test;

/**
 * 긴급 알림 규칙: 기사 > 종류(KEV 우선), 1건·여러 건 글, 번역 제목 우선, 긴 제목 말줄임
 */
class UrgentAlertPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-26T00:00:00Z");

    private final Source kisaSource = createSource(SourceCode.KISA, ArticleKind.ADVISORY, ArticleRegion.DOMESTIC, FeedFormat.RSS, NOW);
    private final Source nvdSource = createSource(SourceCode.NVD, ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, FeedFormat.NVD_API, NOW);

    @Test
    void mapsArticleToCategoryWithKevFirst() {
        assertThat(UrgentAlertPolicy.toAlertCategory(createArticle(nvdSource, "CVE-2026-1 flaw", true, NOW))).isEqualTo(AlertCategory.KEV);
        assertThat(UrgentAlertPolicy.toAlertCategory(createArticle(kisaSource, "긴급 보안 업데이트 권고", false, NOW)))
                .isEqualTo(AlertCategory.KISA_URGENT);
        assertThat(UrgentAlertPolicy.toAlertCategory(createArticle(nvdSource, "CVE-2026-2 flaw", false, NOW)))
                .isEqualTo(AlertCategory.CRITICAL_CVE);
    }

    @Test
    void writesCategoryTitleAndArticleTitleForSingleArticle() {
        UrgentAlertContent content = UrgentAlertPolicy.toContent(List.of(createArticle(kisaSource, "긴급 보안 업데이트 권고", false, NOW)));

        // 저장 전 기사라 번호는 null (저장된 1건 알림은 번호를 실음)
        assertThat(content).isEqualTo(new UrgentAlertContent("KISA 긴급 공지가 올라왔어요", "긴급 보안 업데이트 권고", "vulnerability", null));
        assertThat(UrgentAlertPolicy.toPushData(content)).containsOnlyKeys("targetTab");
        assertThat(UrgentAlertPolicy.toPushData(new UrgentAlertContent("제목", "본문", "vulnerability", 42L)))
                .containsEntry("targetTab", "vulnerability").containsEntry("articleId", "42");
    }

    @Test
    void prefersVerifiedKoreanTitle() {
        Article article = createArticle(nvdSource, "CVE-2026-2: Example remote code execution", false, NOW);
        article.applyAiAnalysis(ArticleTopic.VULNERABILITY, "CVE-2026-2: 원격 코드 실행 예시", ArticleImportance.ROUTINE, null);

        assertThat(UrgentAlertPolicy.toContent(List.of(article)).body()).isEqualTo("CVE-2026-2: 원격 코드 실행 예시");
    }

    @Test
    void writesCountAndMostImportantTitleForSeveralArticles() {
        // 대표 기사: 악용 중(KEV) > KISA 긴급 > 고위험, 같은 종류면 최신 게시
        UrgentAlertContent content = UrgentAlertPolicy.toContent(List.of(
                createArticle(nvdSource, "고위험 취약점", false, NOW),
                createArticle(kisaSource, "긴급 공지", false, NOW),
                createArticle(nvdSource, "악용 중 예전 것", true, NOW.minusSeconds(3600)),
                createArticle(nvdSource, "악용 중 최신 것", true, NOW)));

        assertThat(content.title()).isEqualTo("긴급 보안 소식 4건이 들어왔어요");
        assertThat(content.body()).isEqualTo("악용 중 최신 것 외 3건");
    }

    @Test
    void shortensLongTitleWithEllipsis() {
        UrgentAlertContent content = UrgentAlertPolicy.toContent(List.of(createArticle(nvdSource, "가".repeat(300), false, NOW)));

        assertThat(content.body()).hasSize(UrgentAlertPolicy.MAXIMUM_BODY_TITLE_LENGTH).endsWith("…");
    }

    private static Article createArticle(Source source, String title, boolean isKev, Instant publishedAt) {
        return Article.create(source, new ArticleDraft(source.getKind(), source.getRegion(), title,
                "https://example.test/" + System.nanoTime(), source.getDisplayName(), publishedAt, false,
                null, isKev ? null : new BigDecimal("9.8"), isKev ? null : "3.1", isKev, true), NOW);
    }
}
