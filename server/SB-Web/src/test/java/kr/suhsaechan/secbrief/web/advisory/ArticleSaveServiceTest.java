package kr.suhsaechan.secbrief.web.advisory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.constant.SummaryStatus;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.Source;
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
 * 저장·병합: 같은 CVE 는 한 행 (NVD 링크 원본, KEV 표시 유지), CVE 없는 항목은 원문 링크로 중복 제거
 * 링크 묶음 조회, 두 출처가 같은 CVE 를 동시에 저장해도 병합 유지
 */
@SpringBootTest
@ActiveProfiles("test")
class ArticleSaveServiceTest extends PostgresContainerTestSupport {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

    @Autowired
    private ArticleSaveService articleSaveService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private SourceRepository sourceRepository;

    private Source kevSource;
    private Source nvdSource;
    private Source ghsaSource;
    private Source dailysecuSource;

    @BeforeEach
    void loadSeededSourcesAndClearArticles() {
        articleRepository.deleteAll();
        kevSource = sourceRepository.findByCode(SourceCode.CISA_KEV).orElseThrow();
        nvdSource = sourceRepository.findByCode(SourceCode.NVD).orElseThrow();
        ghsaSource = sourceRepository.findByCode(SourceCode.GHSA).orElseThrow();
        dailysecuSource = sourceRepository.findByCode(SourceCode.DAILYSECU).orElseThrow();
    }

    @Test
    void mergesKevFirstThenNvdIntoSingleNvdRow() {
        saveOrMerge(kevSource, createKevDraft("CVE-2026-1111"));
        SaveOutcome outcome = saveOrMerge(nvdSource, createNvdDraft("CVE-2026-1111", "9.8"));

        Article merged = articleRepository.findByCveId("CVE-2026-1111").orElseThrow();
        assertThat(outcome).isEqualTo(SaveOutcome.MERGED);
        assertThat(articleRepository.count()).isEqualTo(1);
        assertThat(merged.getOriginalUrl()).isEqualTo("https://nvd.nist.gov/vuln/detail/CVE-2026-1111");
        assertThat(merged.getPublisherName()).isEqualTo("NVD");
        assertThat(merged.isKev()).isTrue();
        assertThat(merged.isUrgent()).isTrue();
        assertThat(merged.getCvssScore()).isEqualByComparingTo("9.8");
        assertThat(merged.getAiAnalysisState().getStatus()).isEqualTo(AiStatus.PENDING);
    }

    @Test
    void marksExistingNvdRowAsKevAndKeepsNvdLink() {
        saveOrMerge(nvdSource, createNvdDraft("CVE-2026-2222", "9.1"));
        SaveOutcome outcome = saveOrMerge(kevSource, createKevDraft("CVE-2026-2222"));

        Article merged = articleRepository.findByCveId("CVE-2026-2222").orElseThrow();
        assertThat(outcome).isEqualTo(SaveOutcome.MERGED);
        assertThat(merged.isKev()).isTrue();
        assertThat(merged.getPublisherName()).isEqualTo("NVD");
    }

    @Test
    void ghsaWithCveAlreadyFromNvdKeepsNvdLink() {
        saveOrMerge(nvdSource, createNvdDraft("CVE-2026-5555", "9.8"));

        assertThat(saveOrMerge(ghsaSource, createGhsaDraft("CVE-2026-5555", "GHSA-aaaa-bbbb-cccc")))
                .isEqualTo(SaveOutcome.DUPLICATE);
        assertThat(articleRepository.findByCveId("CVE-2026-5555").orElseThrow().getPublisherName()).isEqualTo("NVD");
    }

    @Test
    void replacesGhsaLinkWhenNvdArrivesLater() {
        saveOrMerge(ghsaSource, createGhsaDraft("CVE-2026-6666", "GHSA-dddd-eeee-ffff"));

        assertThat(saveOrMerge(nvdSource, createNvdDraft("CVE-2026-6666", "9.4"))).isEqualTo(SaveOutcome.MERGED);
        assertThat(articleRepository.findByCveId("CVE-2026-6666").orElseThrow().getOriginalUrl())
                .isEqualTo("https://nvd.nist.gov/vuln/detail/CVE-2026-6666");
    }

    @Test
    void dedupsGhsaWithoutCveByHtmlUrl() {
        ArticleDraft draft = createGhsaDraft(null, "GHSA-gggg-hhhh-iiii");

        assertThat(saveOrMerge(ghsaSource, draft)).isEqualTo(SaveOutcome.CREATED);
        assertThat(saveOrMerge(ghsaSource, draft)).isEqualTo(SaveOutcome.DUPLICATE);
    }

    @Test
    void returnsDuplicateForSameRssUrl() {
        ArticleDraft draft = createNewsDraft("https://www.dailysecu.com/news/articleView.html?idxno=1");

        assertThat(saveOrMerge(dailysecuSource, draft)).isEqualTo(SaveOutcome.CREATED);
        assertThat(saveOrMerge(dailysecuSource, draft)).isEqualTo(SaveOutcome.DUPLICATE);
        assertThat(articleRepository.count()).isEqualTo(1);
    }

    @Test
    void newArticleStartsPendingGeneral() {
        saveOrMerge(dailysecuSource, createNewsDraft("https://www.dailysecu.com/news/articleView.html?idxno=2"));

        Article article = articleRepository.findAll().get(0);
        assertThat(article.getAiAnalysisState().getTopic()).isEqualTo(ArticleTopic.GENERAL);
        assertThat(article.getAiAnalysisState().getStatus()).isEqualTo(AiStatus.PENDING);
        assertThat(article.getAiAnalysisState().getAttemptCount()).isZero();
        assertThat(article.getAiAnalysisState().getTranslatedTitle()).isNull();
    }

    @Test
    void findsSettledUrlsAcrossLookupChunks() {
        // 조회 목록 1200건 (묶음 500건 기준 3번 조회), 요약 입력이 있는 저장 링크는 첫 묶음·마지막 묶음에 1건씩
        List<String> lookupUrls = new ArrayList<>(IntStream.range(0, 1200)
                .mapToObj(number -> "https://www.dailysecu.com/news/articleView.html?idxno=" + number)
                .toList());
        saveOrMerge(dailysecuSource, createNewsDraftWithSourceText(lookupUrls.get(3)));
        saveOrMerge(dailysecuSource, createNewsDraftWithSourceText(lookupUrls.get(1150)));
        saveOrMerge(dailysecuSource, createNewsDraftWithSourceText("https://www.dailysecu.com/news/articleView.html?idxno=not-in-lookup"));
        // 요약 입력·이미지 주소가 없는 저장 링크: 채울 것이 남아 건너뛰지 않음
        saveOrMerge(dailysecuSource, createNewsDraft(lookupUrls.get(700)));

        assertThat(articleSaveService.findSettledOriginalUrls(lookupUrls))
                .containsExactlyInAnyOrder(lookupUrls.get(3), lookupUrls.get(1150));
    }

    @Test
    void fillsMissingSourceTextAndImageWhenSameLinkIsCollectedAgain() {
        String originalUrl = "https://www.dailysecu.com/news/articleView.html?idxno=backfill";
        saveOrMerge(dailysecuSource, createNewsDraft(originalUrl));

        // 기능 이전에 쌓인 기사: 다시 수집한 요약 입력으로 채우고 요약 대기로
        assertThat(saveOrMerge(dailysecuSource, createNewsDraftWithSourceText(originalUrl))).isEqualTo(SaveOutcome.MERGED);
        Article article = articleRepository.findByOriginalUrl(originalUrl).orElseThrow();
        assertThat(article.getSummaryState().getStatus()).isEqualTo(SummaryStatus.PENDING);
        assertThat(articleRepository.count()).isEqualTo(1);

        // 이미 채운 뒤에는 변경 없음
        assertThat(saveOrMerge(dailysecuSource, createNewsDraftWithSourceText(originalUrl))).isEqualTo(SaveOutcome.DUPLICATE);
    }

    @Test
    void returnsEmptyWhenNoUrlIsStored() {
        assertThat(articleSaveService.findSettledOriginalUrls(List.of("https://www.dailysecu.com/news/none"))).isEmpty();
        assertThat(articleSaveService.findSettledOriginalUrls(List.of())).isEmpty();
    }

    @Test
    void keepsKevMarkAndNvdLinkWhenTwoSourcesSaveSameCvesConcurrently() throws Exception {
        List<String> cveIds = IntStream.range(0, 40).mapToObj(number -> "CVE-2026-9" + String.format("%03d", number)).toList();
        CountDownLatch startTogether = new CountDownLatch(1);

        CompletableFuture<Void> kevSaving = CompletableFuture.runAsync(() -> {
            awaitStart(startTogether);
            cveIds.forEach(cveId -> saveOrMerge(kevSource, createKevDraft(cveId)));
        });
        CompletableFuture<Void> nvdSaving = CompletableFuture.runAsync(() -> {
            awaitStart(startTogether);
            cveIds.forEach(cveId -> saveOrMerge(nvdSource, createNvdDraft(cveId, "9.8")));
        });
        startTogether.countDown();
        // 유일 제약 위반이 재시도 없이 새어 나오면 여기서 예외
        CompletableFuture.allOf(kevSaving, nvdSaving).get(60, TimeUnit.SECONDS);

        List<Article> articles = articleRepository.findAll();
        assertThat(articles).hasSize(cveIds.size());
        assertThat(articles).allSatisfy(article -> {
            assertThat(article.isKev()).isTrue();
            assertThat(article.getPublisherName()).isEqualTo("NVD");
            assertThat(article.getOriginalUrl()).isEqualTo("https://nvd.nist.gov/vuln/detail/" + article.getCveId());
        });
    }

    private static void awaitStart(CountDownLatch startTogether) {
        try {
            startTogether.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private SaveOutcome saveOrMerge(Source source, ArticleDraft draft) {
        return articleSaveService.saveOrMerge(source, draft, NOW);
    }

    private static ArticleDraft createKevDraft(String cveId) {
        return createVulnerabilityDraft(cveId + ": Example product vulnerability",
                "https://www.cisa.gov/known-exploited-vulnerabilities-catalog?search_api_fulltext=" + cveId,
                "CISA KEV", cveId, null, null, true);
    }

    private static ArticleDraft createNvdDraft(String cveId, String cvssScore) {
        return createVulnerabilityDraft(cveId + ": Example remote code execution",
                "https://nvd.nist.gov/vuln/detail/" + cveId, "NVD", cveId, new BigDecimal(cvssScore), "3.1", false);
    }

    private static ArticleDraft createGhsaDraft(String cveId, String ghsaId) {
        return createVulnerabilityDraft("Example package: remote code execution",
                "https://github.com/advisories/" + ghsaId, "GitHub Advisory", cveId, new BigDecimal("9.8"), "3.1", false);
    }

    private static ArticleDraft createVulnerabilityDraft(String title, String originalUrl, String publisherName,
                                                         String cveId, BigDecimal cvssScore, String cvssVersion, boolean isKev) {
        boolean isUrgent = isKev || (cvssScore != null && cvssScore.compareTo(new BigDecimal("9.0")) >= 0);
        return new ArticleDraft(ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, title, originalUrl, publisherName,
                NOW, false, cveId, cvssScore, cvssVersion, isKev, isUrgent);
    }

    private static ArticleDraft createNewsDraftWithSourceText(String originalUrl) {
        return new ArticleDraft(ArticleKind.NEWS, ArticleRegion.DOMESTIC, "보안 소식 제목", originalUrl, "데일리시큐",
                NOW, false, null, null, null, false, false, null,
                "피드가 준 요약문이에요. 요약 입력으로 쓸 만큼 충분히 긴 글이어야 해서 문장을 조금 더 붙였어요.", List.of());
    }

    private static ArticleDraft createNewsDraft(String originalUrl) {
        return new ArticleDraft(ArticleKind.NEWS, ArticleRegion.DOMESTIC, "보안 소식 제목", originalUrl, "데일리시큐",
                NOW, false, null, null, null, false, false);
    }
}
