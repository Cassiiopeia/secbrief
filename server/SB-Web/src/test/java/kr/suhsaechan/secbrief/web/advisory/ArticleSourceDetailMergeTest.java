package kr.suhsaechan.secbrief.web.advisory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.constant.SummaryStatus;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;
import kr.suhsaechan.secbrief.advisory.fact.SourceFactLabel;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.advisory.service.ArticleSaveService;
import kr.suhsaechan.secbrief.advisory.service.SaveOutcome;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 같은 CVE 병합 (DB): 사실 값이 사라지지 않음 (KEV > NVD 링크 교체, NVD > KEV 등재 표시), 요약 입력은 NVD 설명으로 다시 대기
 * 같은 값을 다시 수집하면 변경 없음(DUPLICATE)
 */
@SpringBootTest
@ActiveProfiles("test")
class ArticleSourceDetailMergeTest extends PostgresContainerTestSupport {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
    private static final String CVE_ID = "CVE-2026-1111";
    private static final String KEV_SOURCE_TEXT = "ExampleVendor ExampleServer contains a deserialization vulnerability.";
    private static final String NVD_SOURCE_TEXT = "A deserialization flaw in ExampleServer allows remote code execution.";
    private static final SourceFact KEV_DUE_DATE = new SourceFact(SourceFactLabel.ACTION_DUE_DATE, "2026-11-23");
    private static final SourceFact KEV_WEAKNESS = new SourceFact(SourceFactLabel.WEAKNESS, "CWE-502");
    private static final SourceFact NVD_SCORE = new SourceFact(SourceFactLabel.CVSS_SCORE, "9.8");
    private static final SourceFact NVD_WEAKNESS = new SourceFact(SourceFactLabel.WEAKNESS, "CWE-94");

    @Autowired
    private ArticleSaveService articleSaveService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private SourceRepository sourceRepository;

    private Source kevSource;
    private Source nvdSource;

    @BeforeEach
    void loadSeededSourcesAndClearArticles() {
        articleRepository.deleteAll();
        kevSource = sourceRepository.findByCode(SourceCode.CISA_KEV).orElseThrow();
        nvdSource = sourceRepository.findByCode(SourceCode.NVD).orElseThrow();
    }

    @Test
    void keepsKevFactsWhenNvdReplacesLink() {
        articleSaveService.saveOrMerge(kevSource, createKevDraft(), NOW);
        SaveOutcome outcome = articleSaveService.saveOrMerge(nvdSource, createNvdDraft(), NOW);

        Article merged = articleRepository.findByCveId(CVE_ID).orElseThrow();
        assertThat(outcome).isEqualTo(SaveOutcome.MERGED);
        assertThat(merged.getPublisherName()).isEqualTo("NVD");
        assertThat(merged.getSourceFacts()).containsExactly(KEV_DUE_DATE, NVD_SCORE, NVD_WEAKNESS);
        assertThat(merged.getSummaryState().getStatus()).isEqualTo(SummaryStatus.PENDING);
        assertThat(merged.getSummaryState().getSourceText()).isEqualTo(NVD_SOURCE_TEXT);
    }

    @Test
    void addsKevFactsToExistingNvdRowAndKeepsNvdSourceText() {
        articleSaveService.saveOrMerge(nvdSource, createNvdDraft(), NOW);
        SaveOutcome outcome = articleSaveService.saveOrMerge(kevSource, createKevDraft(), NOW);

        Article merged = articleRepository.findByCveId(CVE_ID).orElseThrow();
        assertThat(outcome).isEqualTo(SaveOutcome.MERGED);
        assertThat(merged.isKev()).isTrue();
        assertThat(merged.getSourceFacts()).containsExactly(NVD_SCORE, NVD_WEAKNESS, KEV_DUE_DATE);
        assertThat(merged.getSummaryState().getSourceText()).isEqualTo(NVD_SOURCE_TEXT);
    }

    @Test
    void returnsDuplicateWhenSameSourceIsCollectedAgain() {
        articleSaveService.saveOrMerge(nvdSource, createNvdDraft(), NOW);

        assertThat(articleSaveService.saveOrMerge(nvdSource, createNvdDraft(), NOW)).isEqualTo(SaveOutcome.DUPLICATE);
        assertThat(articleRepository.findByCveId(CVE_ID).orElseThrow().getSourceFacts())
                .containsExactly(NVD_SCORE, NVD_WEAKNESS);
    }

    private static ArticleDraft createKevDraft() {
        return new ArticleDraft(ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, CVE_ID + ": ExampleServer deserialization",
                "https://www.cisa.gov/known-exploited-vulnerabilities-catalog?search_api_fulltext=" + CVE_ID, "CISA KEV",
                NOW, false, CVE_ID, null, null, true, true, null, KEV_SOURCE_TEXT, List.of(KEV_DUE_DATE, KEV_WEAKNESS));
    }

    private static ArticleDraft createNvdDraft() {
        return new ArticleDraft(ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, CVE_ID + ": A deserialization flaw",
                "https://nvd.nist.gov/vuln/detail/" + CVE_ID, "NVD", NOW, false, CVE_ID, new BigDecimal("9.8"), "3.1",
                false, true, null, NVD_SOURCE_TEXT, List.of(NVD_SCORE, NVD_WEAKNESS));
    }
}
