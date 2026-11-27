package kr.suhsaechan.secbrief.advisory.entity;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import org.junit.jupiter.api.Test;

/**
 * 기사 키워드 상태: 새 기사는 없음, 교체는 통째로, AI 실패·제목 교체(NVD 링크로 바뀜) 때 비움, 목록은 대소문자 무시 글자순
 */
class ArticleKeywordStateTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

    private final Article article = Article.create(
            createSource(SourceCode.GHSA, ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, FeedFormat.GHSA_API, null),
            new ArticleDraft(ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, "Example Web Framework: remote code execution",
                    "https://github.com/advisories/GHSA-aaaa-bbbb-cccc", "GitHub Advisory", NOW, false,
                    "CVE-2026-5555", new BigDecimal("9.8"), "3.1", false, true),
            NOW);

    @Test
    void newArticleHasNoKeywords() {
        assertThat(article.getAiAnalysisState().getKeywords()).isEmpty();
    }

    @Test
    void replacesKeywordsAsWholeAndListsThemInCaseInsensitiveOrder() {
        article.replaceKeywords(List.of("원격 코드 실행", "linux", "Example Web Framework"));
        article.replaceKeywords(List.of("제로데이", "apache", "Linux"));

        assertThat(article.getAiAnalysisState().getKeywords()).containsExactly("apache", "Linux", "제로데이");
    }

    @Test
    void clearsKeywordsWhenAiFails() {
        article.replaceKeywords(List.of("원격 코드 실행"));

        article.recordAiFailure();

        assertThat(article.getAiAnalysisState().getKeywords()).isEmpty();
    }

    @Test
    void clearsKeywordsWhenTitleIsReplacedByNvd() {
        article.replaceKeywords(List.of("원격 코드 실행"));

        article.replaceLinkWithNvd(
                createSource(SourceCode.NVD, ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, FeedFormat.NVD_API, null),
                new ArticleDraft(ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, "CVE-2026-5555: Example remote code execution",
                        "https://nvd.nist.gov/vuln/detail/CVE-2026-5555", "NVD", NOW, false,
                        "CVE-2026-5555", new BigDecimal("9.8"), "3.1", false, true));

        assertThat(article.getAiAnalysisState().getKeywords()).isEmpty();
    }
}
