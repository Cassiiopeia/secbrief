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
import kr.suhsaechan.secbrief.advisory.constant.SummaryStatus;
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;
import kr.suhsaechan.secbrief.advisory.fact.SourceFactLabel;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.summary.AffectedTarget;
import kr.suhsaechan.secbrief.advisory.summary.ArticleBrief;
import kr.suhsaechan.secbrief.advisory.support.LegacySummaryFixtures;
import org.junit.jupiter.api.Test;

/**
 * 기사 요약 상태·사실 값 전이: 입력 있음 > PENDING / 없음 > NONE, 브리핑 반영 > DONE + 입력 비움 + 이전 구조 3개도 채움, 실패 3회 > FAILED + 입력 비움
 * 같은 CVE 병합: 사실 값이 사라지지 않음 (KEV 등재 표시는 새 라벨만 추가, NVD 링크 교체는 NVD 라벨만 교체 + 요약 다시 대기)
 */
class ArticleSummaryStateTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
    private static final String CVE_ID = "CVE-2026-1111";
    private static final String KEV_SOURCE_TEXT = "ExampleVendor ExampleServer contains a deserialization vulnerability.";
    private static final String NVD_SOURCE_TEXT = "A deserialization flaw in ExampleServer allows remote code execution.";
    private static final List<SourceFact> KEV_FACTS = List.of(
            new SourceFact(SourceFactLabel.VENDOR_PRODUCT, "ExampleVendor ExampleServer"),
            new SourceFact(SourceFactLabel.ACTION_DUE_DATE, "2026-11-23"),
            new SourceFact(SourceFactLabel.WEAKNESS, "CWE-502"));
    private static final List<SourceFact> NVD_FACTS = List.of(
            new SourceFact(SourceFactLabel.CVSS_SCORE, "9.8"),
            new SourceFact(SourceFactLabel.WEAKNESS, "CWE-94"));

    private static final ArticleBrief BRIEF = new ArticleBrief("ExampleServer에서 원격 코드 실행이 가능해요.",
            "ExampleServer에 역직렬화 취약점이 있어요.", "원격에서 코드를 실행할 수 있어요.",
            List.of(new AffectedTarget("ExampleServer", "2.0 이전 버전"), new AffectedTarget("ExampleVendor", null)),
            List.of("패치를 적용하세요.", "관리 포트를 닫으세요."));

    private final Source kevSource =
            createSource(SourceCode.CISA_KEV, ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, FeedFormat.CISA_KEV_JSON, null);
    private final Source nvdSource =
            createSource(SourceCode.NVD, ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, FeedFormat.NVD_API, null);

    @Test
    void startsPendingWithSourceTextAndFacts() {
        Article article = Article.create(kevSource, createKevDraft(KEV_SOURCE_TEXT, KEV_FACTS), NOW);

        assertThat(article.getSummaryState().getStatus()).isEqualTo(SummaryStatus.PENDING);
        assertThat(article.getSummaryState().getSourceText()).isEqualTo(KEV_SOURCE_TEXT);
        assertThat(article.getSummaryState().isSummarized()).isFalse();
        assertThat(article.getSourceFacts()).isEqualTo(KEV_FACTS);
    }

    @Test
    void startsNoneWithoutSourceText() {
        Article article = Article.create(kevSource, createKevDraft(null, List.of()), NOW);

        assertThat(article.getSummaryState().getStatus()).isEqualTo(SummaryStatus.NONE);
        assertThat(article.getSourceFacts()).isEmpty();
        assertThat(article.getSourceFactsJson()).isNull();
    }

    @Test
    void appliesBriefWithLegacyFieldsAndClearsSourceText() {
        Article article = Article.create(kevSource, createKevDraft(KEV_SOURCE_TEXT, KEV_FACTS), NOW);

        article.getSummaryState().applyBrief(BRIEF);

        ArticleSummaryState summaryState = article.getSummaryState();
        assertThat(summaryState.getStatus()).isEqualTo(SummaryStatus.DONE);
        assertThat(summaryState.isSummarized()).isTrue();
        assertThat(summaryState.hasBrief()).isTrue();
        assertThat(summaryState.getBrief()).isEqualTo(BRIEF);
        assertThat(summaryState.getSourceText()).isNull();
        // 이전 구조 3개: 요약 = 한 줄 결론 + 무슨 일 + 왜 중요, 핵심 정리 = 영향 대상 줄, 대응 안내 = 대응을 이은 글
        assertThat(summaryState.getSummary()).isEqualTo(
                "ExampleServer에서 원격 코드 실행이 가능해요. ExampleServer에 역직렬화 취약점이 있어요. 원격에서 코드를 실행할 수 있어요.");
        assertThat(summaryState.getKeyPoints()).containsExactly("ExampleServer: 2.0 이전 버전", "ExampleVendor");
        assertThat(summaryState.getActionGuide()).isEqualTo("패치를 적용하세요. 관리 포트를 닫으세요.");
    }

    @Test
    void briefWithoutOptionalFieldsLeavesListColumnsNullAndActionGuideNull() {
        ArticleSummaryState summaryState = Article.create(kevSource, createKevDraft(KEV_SOURCE_TEXT, KEV_FACTS), NOW).getSummaryState();

        summaryState.applyBrief(new ArticleBrief("한 줄 결론이에요.", "무슨 일이에요.", null, List.of(), List.of()));

        assertThat(summaryState.getBrief()).isEqualTo(new ArticleBrief("한 줄 결론이에요.", "무슨 일이에요.", null, List.of(), List.of()));
        assertThat(summaryState.getAffectedJson()).isNull();
        assertThat(summaryState.getActionsJson()).isNull();
        assertThat(summaryState.getSummary()).isEqualTo("한 줄 결론이에요. 무슨 일이에요.");
        assertThat(summaryState.getKeyPoints()).isEmpty();
        assertThat(summaryState.getActionGuide()).isNull();
    }

    @Test
    void legacySummaryRowHasNoBriefButStaysSummarized() {
        // 이전 구조만 있는 행 (V0.8.0 이전에 쓴 요약): 브리핑 열이 모두 null
        ArticleSummaryState summaryState = Article.create(kevSource, createKevDraft(KEV_SOURCE_TEXT, KEV_FACTS), NOW).getSummaryState();
        LegacySummaryFixtures.applyLegacySummary(summaryState, "이전 구조의 요약이에요.", List.of("핵심이에요."), "패치를 적용하세요.");

        assertThat(summaryState.isSummarized()).isTrue();
        assertThat(summaryState.hasBrief()).isFalse();
        assertThat(summaryState.getSummary()).isEqualTo("이전 구조의 요약이에요.");
        assertThat(summaryState.getKeyPoints()).containsExactly("핵심이에요.");
    }

    @Test
    void blankOneLineIsNotABrief() {
        ArticleSummaryState summaryState = Article.create(kevSource, createKevDraft(KEV_SOURCE_TEXT, KEV_FACTS), NOW).getSummaryState();
        summaryState.applyBrief(new ArticleBrief(" ", "무슨 일이에요.", null, List.of(), List.of()));

        // 앱은 한 줄 결론이 없는 브리핑을 쓰지 않으므로 브리핑 없음으로 내보냄 (이전 구조 값은 그대로)
        assertThat(summaryState.hasBrief()).isFalse();
        assertThat(summaryState.isSummarized()).isTrue();
    }

    @Test
    void staysPendingUntilThirdFailureThenClearsSourceText() {
        ArticleSummaryState summaryState = Article.create(kevSource, createKevDraft(KEV_SOURCE_TEXT, KEV_FACTS), NOW).getSummaryState();

        summaryState.recordFailure();
        summaryState.recordFailure();
        assertThat(summaryState.getStatus()).isEqualTo(SummaryStatus.PENDING);
        assertThat(summaryState.getSourceText()).isEqualTo(KEV_SOURCE_TEXT);

        summaryState.recordFailure();
        assertThat(summaryState.getStatus()).isEqualTo(SummaryStatus.FAILED);
        assertThat(summaryState.getAttemptCount()).isEqualTo(ArticleSummaryState.MAXIMUM_ATTEMPT_COUNT);
        assertThat(summaryState.getSourceText()).isNull();
        assertThat(summaryState.isSummarized()).isFalse();
    }

    @Test
    void nvdLinkReplacementKeepsKevFactsAndRestartsSummary() {
        Article article = Article.create(kevSource, createKevDraft(KEV_SOURCE_TEXT, KEV_FACTS), NOW);
        article.getSummaryState().applyBrief(BRIEF);

        article.replaceLinkWithNvd(nvdSource, createNvdDraft(NVD_SOURCE_TEXT, NVD_FACTS));

        // KEV 고유 값 유지, 같은 라벨(CWE)은 NVD 값
        assertThat(article.getSourceFacts()).containsExactly(
                new SourceFact(SourceFactLabel.VENDOR_PRODUCT, "ExampleVendor ExampleServer"),
                new SourceFact(SourceFactLabel.ACTION_DUE_DATE, "2026-11-23"),
                new SourceFact(SourceFactLabel.CVSS_SCORE, "9.8"),
                new SourceFact(SourceFactLabel.WEAKNESS, "CWE-94"));
        ArticleSummaryState summaryState = article.getSummaryState();
        assertThat(summaryState.getStatus()).isEqualTo(SummaryStatus.PENDING);
        assertThat(summaryState.getSourceText()).isEqualTo(NVD_SOURCE_TEXT);
        assertThat(summaryState.getSummary()).isNull();
        assertThat(summaryState.getKeyPoints()).isEmpty();
        assertThat(summaryState.getActionGuide()).isNull();
        // 브리핑 열도 함께 비움
        assertThat(summaryState.hasBrief()).isFalse();
        assertThat(summaryState.getOneLine()).isNull();
        assertThat(summaryState.getWhatHappened()).isNull();
        assertThat(summaryState.getWhyItMatters()).isNull();
        assertThat(summaryState.getAffectedJson()).isNull();
        assertThat(summaryState.getActionsJson()).isNull();
        assertThat(summaryState.getAttemptCount()).isZero();
    }

    @Test
    void nvdLinkReplacementWithoutDescriptionKeepsExistingSummary() {
        Article article = Article.create(kevSource, createKevDraft(KEV_SOURCE_TEXT, KEV_FACTS), NOW);
        article.getSummaryState().applyBrief(BRIEF);

        article.replaceLinkWithNvd(nvdSource, createNvdDraft(null, NVD_FACTS));

        assertThat(article.getSummaryState().getStatus()).isEqualTo(SummaryStatus.DONE);
        assertThat(article.getSummaryState().getBrief()).isEqualTo(BRIEF);
    }

    @Test
    void kevMergeAddsOnlyNewLabelsAndKeepsSummary() {
        Article article = Article.create(nvdSource, createNvdDraft(NVD_SOURCE_TEXT, NVD_FACTS), NOW);
        article.getSummaryState().applyBrief(BRIEF);

        boolean isChanged = article.mergeSourceDetails(createKevDraft(KEV_SOURCE_TEXT, KEV_FACTS));

        assertThat(isChanged).isTrue();
        assertThat(article.getSourceFacts()).containsExactly(
                new SourceFact(SourceFactLabel.CVSS_SCORE, "9.8"),
                new SourceFact(SourceFactLabel.WEAKNESS, "CWE-94"),
                new SourceFact(SourceFactLabel.VENDOR_PRODUCT, "ExampleVendor ExampleServer"),
                new SourceFact(SourceFactLabel.ACTION_DUE_DATE, "2026-11-23"));
        assertThat(article.getSummaryState().getStatus()).isEqualTo(SummaryStatus.DONE);
        assertThat(article.getSummaryState().getBrief()).isEqualTo(BRIEF);
    }

    @Test
    void mergingSameDetailsAgainChangesNothing() {
        Article article = Article.create(kevSource, createKevDraft(KEV_SOURCE_TEXT, KEV_FACTS), NOW);

        assertThat(article.mergeSourceDetails(createKevDraft(KEV_SOURCE_TEXT, KEV_FACTS))).isFalse();
        assertThat(article.getSourceFacts()).isEqualTo(KEV_FACTS);
        assertThat(article.getSummaryState().getAttemptCount()).isZero();
    }

    @Test
    void mergeAdoptsSourceTextOnlyWhenArticleHadNone() {
        Article articleWithoutSourceText = Article.create(nvdSource, createNvdDraft(null, NVD_FACTS), NOW);
        Article failedArticle = Article.create(nvdSource, createNvdDraft(NVD_SOURCE_TEXT, NVD_FACTS), NOW);
        failedArticle.getSummaryState().recordFailure();
        failedArticle.getSummaryState().recordFailure();
        failedArticle.getSummaryState().recordFailure();

        assertThat(articleWithoutSourceText.mergeSourceDetails(createKevDraft(KEV_SOURCE_TEXT, List.of()))).isTrue();
        failedArticle.mergeSourceDetails(createKevDraft(KEV_SOURCE_TEXT, List.of()));

        assertThat(articleWithoutSourceText.getSummaryState().getStatus()).isEqualTo(SummaryStatus.PENDING);
        assertThat(articleWithoutSourceText.getSummaryState().getSourceText()).isEqualTo(KEV_SOURCE_TEXT);
        // 3회 실패한 기사는 다른 출처 글로 다시 시도하지 않음
        assertThat(failedArticle.getSummaryState().getStatus()).isEqualTo(SummaryStatus.FAILED);
        assertThat(failedArticle.getSummaryState().getSourceText()).isNull();
    }

    private static ArticleDraft createKevDraft(String sourceText, List<SourceFact> sourceFacts) {
        return new ArticleDraft(ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, CVE_ID + ": ExampleServer deserialization",
                "https://www.cisa.gov/known-exploited-vulnerabilities-catalog?search_api_fulltext=" + CVE_ID, "CISA KEV",
                NOW, false, CVE_ID, null, null, true, true, null, sourceText, sourceFacts);
    }

    private static ArticleDraft createNvdDraft(String sourceText, List<SourceFact> sourceFacts) {
        return new ArticleDraft(ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, CVE_ID + ": A deserialization flaw",
                "https://nvd.nist.gov/vuln/detail/" + CVE_ID, "NVD",
                NOW, false, CVE_ID, new BigDecimal("9.8"), "3.1", false, true, null, sourceText, sourceFacts);
    }
}
