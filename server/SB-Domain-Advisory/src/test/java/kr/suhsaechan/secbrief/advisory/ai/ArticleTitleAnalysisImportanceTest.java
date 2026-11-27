package kr.suhsaechan.secbrief.advisory.ai;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Test;

/**
 * AI 중요도·회사·제품 이름: 응답 해석(없는 값은 ROUTINE·null), 제목에 없는 이름 버림, 기사 중요도는 규칙 하한 아래로 내려가지 않음
 */
class ArticleTitleAnalysisImportanceTest {

    private static final Instant NOW = Instant.parse("2026-11-26T00:00:00Z");
    private final Source nvdSource = createSource(SourceCode.NVD, ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, FeedFormat.NVD_API, NOW);

    @Test
    void readsImportanceAndSubjectNameFromResponse() {
        List<ArticleTitleAnalysisOutcome> outcomes = ArticleTitleAnalysisPrompt.toOutcomes("""
                {"results":[
                  {"number":1,"topic":"remoteAccess","translatedTitle":"번역","importance":"headline","subjectName":"Palo Alto Networks"},
                  {"number":2,"topic":"general","translatedTitle":null,"importance":"urgent!!","subjectName":null},
                  {"number":3,"topic":"general","translatedTitle":null}
                ]}""", 3);

        assertThat(outcomes.get(0).analysis().importance()).isEqualTo(ArticleImportance.HEADLINE);
        assertThat(outcomes.get(0).analysis().subjectName()).isEqualTo("Palo Alto Networks");
        // 모르는 값·빠진 값: 그 건을 실패시키지 않고 ROUTINE·null
        assertThat(outcomes.get(1).analysis().importance()).isEqualTo(ArticleImportance.ROUTINE);
        assertThat(outcomes.get(2).analysis().importance()).isEqualTo(ArticleImportance.ROUTINE);
        assertThat(outcomes.get(2).analysis().subjectName()).isNull();
    }

    @Test
    void outputSchemaRequiresImportanceAndSubjectName() {
        String schemaText = ArticleTitleAnalysisPrompt.toOutputJsonSchema().toString();

        assertThat(schemaText).contains("importance", "subjectName", "headline", "notable", "routine");
    }

    @Test
    void acceptsOnlySubjectNameWrittenInTitle() {
        String title = "Palo Alto Networks warns of flaw exploited in PAN-OS attacks";

        assertThat(ArticleTitleAnalysisPolicy.toAcceptedSubjectName(title, "  palo alto networks ")).isEqualTo("palo alto networks");
        // 제목에 없는 이름, 빈 값, 80자 초과는 버림
        assertThat(ArticleTitleAnalysisPolicy.toAcceptedSubjectName(title, "Cisco")).isNull();
        assertThat(ArticleTitleAnalysisPolicy.toAcceptedSubjectName(title, " ")).isNull();
        assertThat(ArticleTitleAnalysisPolicy.toAcceptedSubjectName("가".repeat(200), "가".repeat(81))).isNull();
        assertThat(ArticleTitleAnalysisPolicy.toAcceptedSubjectName(title, null)).isNull();
    }

    @Test
    void articleImportanceNeverDropsBelowRuleFloor() {
        Article kevArticle = createArticle(true);
        assertThat(kevArticle.getImportance()).isEqualTo(ArticleImportance.NOTABLE);

        kevArticle.applyAiAnalysis(ArticleTopic.VULNERABILITY, null, ArticleImportance.ROUTINE, "Example");
        assertThat(kevArticle.getImportance()).isEqualTo(ArticleImportance.NOTABLE);
        assertThat(kevArticle.getAiAnalysisState().getSubjectName()).isEqualTo("Example");

        kevArticle.applyAiAnalysis(ArticleTopic.VULNERABILITY, null, ArticleImportance.HEADLINE, null);
        assertThat(kevArticle.getImportance()).isEqualTo(ArticleImportance.HEADLINE);

        // AI 실패: 규칙 하한으로 되돌리고 이름을 비움
        kevArticle.recordAiFailure();
        assertThat(kevArticle.getImportance()).isEqualTo(ArticleImportance.NOTABLE);
        assertThat(kevArticle.getAiAnalysisState().getSubjectName()).isNull();
    }

    @Test
    void kevListingRaisesRoutineArticleToNotable() {
        Article article = createArticle(false);
        assertThat(article.getImportance()).isEqualTo(ArticleImportance.ROUTINE);

        article.markKev();

        assertThat(article.getImportance()).isEqualTo(ArticleImportance.NOTABLE);
    }

    private Article createArticle(boolean isKev) {
        return Article.create(nvdSource, new ArticleDraft(ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS,
                "CVE-2026-1: Example flaw", "https://example.test/" + System.nanoTime(), "NVD", NOW, false,
                "CVE-2026-1", null, null, isKev, isKev), NOW);
    }
}
