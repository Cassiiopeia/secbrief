package kr.suhsaechan.secbrief.web.advisory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSearchCondition;
import kr.suhsaechan.secbrief.advisory.dto.TodayResponse;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.policy.ArticleUrgencyPolicy;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.advisory.service.ArticleQueryService;
import kr.suhsaechan.secbrief.advisory.service.ArticleSaveService;
import kr.suhsaechan.secbrief.advisory.service.SaveOutcome;
import kr.suhsaechan.secbrief.advisory.service.TodayQueryService;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.ActiveProfiles;

/**
 * 새 출처 (DB): 시드 5곳의 종류·지역·수집 방식, 해외 배포판 권고가 국내 공지 조회·긴급·주요 소식에 섞이지 않음,
 * 오래된 게시분은 오늘 건수·주요 소식에 안 잡힘, 같은 CVE 가 GHSA_HIGH·GHSA·NVD 에서 들어올 때 병합·긴급 규칙
 */
@SpringBootTest
@ActiveProfiles("test")
class NewSourceArticleTest extends PostgresContainerTestSupport {

    @Autowired
    private SourceRepository sourceRepository;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private ArticleSaveService articleSaveService;
    @Autowired
    private ArticleQueryService articleQueryService;
    @Autowired
    private TodayQueryService todayQueryService;

    private Instant now;

    @BeforeEach
    void clearArticles() {
        articleRepository.deleteAll();
        now = Instant.now();
    }

    @Test
    void seedsOverseasAdvisoryRssSourcesAndGhsaHigh() {
        for (String sourceCode : List.of(SourceCode.UBUNTU_USN, SourceCode.DEBIAN_DSA, SourceCode.REDHAT_RHSA, SourceCode.NODEJS_SECURITY)) {
            Source source = findSource(sourceCode);
            assertThat(source.getKind()).isEqualTo(ArticleKind.ADVISORY);
            assertThat(source.getRegion()).isEqualTo(ArticleRegion.OVERSEAS);
            assertThat(source.getFeedFormat()).isEqualTo(FeedFormat.RSS);
        }
        Source ghsaHighSource = findSource(SourceCode.GHSA_HIGH);
        assertThat(ghsaHighSource.getKind()).isEqualTo(ArticleKind.VULNERABILITY);
        assertThat(ghsaHighSource.getFeedFormat()).isEqualTo(FeedFormat.GHSA_API);
        assertThat(ghsaHighSource.getFeedUrl()).endsWith("severity=high");
    }

    @Test
    void overseasDistributionAdvisoriesStayOutOfDomesticNoticeList() {
        saveAdvisory(SourceCode.KISA, "MS 11월 보안 업데이트 권고", Duration.ofHours(1));
        saveAdvisory(SourceCode.UBUNTU_USN, "USN-9203-1: libpcap vulnerabilities", Duration.ofHours(2));
        saveAdvisory(SourceCode.REDHAT_RHSA, "RHSA-2026:103537: Critical: Satellite 6.16 EUS security update", Duration.ofHours(3));

        // 앱의 국내 공지 조회: kind ADVISORY + region DOMESTIC
        assertThat(findItems(ArticleKind.ADVISORY, ArticleRegion.DOMESTIC, null))
                .extracting(ArticleResponse::sourceCode).containsExactly(SourceCode.KISA);
        assertThat(findItems(ArticleKind.ADVISORY, ArticleRegion.OVERSEAS, null))
                .extracting(ArticleResponse::sourceCode).containsExactly(SourceCode.UBUNTU_USN, SourceCode.REDHAT_RHSA);
        assertThat(findItems(null, null, SourceCode.REDHAT_RHSA)).hasSize(1);
    }

    @Test
    void overseasDistributionAdvisoriesAreNeitherUrgentNorHeadline() {
        saveAdvisory(SourceCode.REDHAT_RHSA, "RHSA-2026:103537: Critical: Satellite 6.16 EUS security update", Duration.ofHours(1));
        saveAdvisory(SourceCode.DEBIAN_DSA, "DSA-9001-1 example-server - security update", Duration.ofHours(2));

        TodayResponse today = todayQueryService.findToday();

        assertThat(today.urgentItem()).isNull();
        assertThat(today.headlineItems()).isEmpty();
        assertThat(articleRepository.findAlertCandidates(now, Limit.of(50))).filteredOn(Article::isUrgent).isEmpty();
        assertThat(articleRepository.findAll()).allSatisfy(
                article -> assertThat(article.getImportance()).isEqualTo(ArticleImportance.ROUTINE));
    }

    @Test
    void oldArticlesOfFirstCollectDoNotCountAsTodayOrHeadline() {
        // 첫 수집이 가져온 과거 글: 게시일 기준이라 오늘·이번 주 건수와 주요 소식에 잡히지 않고, 최신 목록에서는 새 글 뒤
        Article oldRelease = saveAdvisory(SourceCode.NODEJS_SECURITY, "Security releases available", Duration.ofDays(20));
        oldRelease.applyAiAnalysis(ArticleTopic.VULNERABILITY, null, ArticleImportance.HEADLINE, null);
        articleRepository.save(oldRelease);
        Article freshNotice = saveAdvisory(SourceCode.KISA, "MS 11월 보안 업데이트 권고", Duration.ZERO);

        TodayResponse today = todayQueryService.findToday();

        assertThat(today.todayCount()).isEqualTo(1);
        assertThat(today.weeklyCount()).isEqualTo(1);
        assertThat(today.headlineItems()).isEmpty();
        assertThat(today.streamItems()).extracting(ArticleResponse::id).containsExactly(freshNotice.getId(), oldRelease.getId());
    }

    @Test
    void sameCveFromGhsaHighThenGhsaThenNvdMergesIntoOneNvdRow() {
        String cveId = "CVE-2026-8801";
        // high 등급으로 먼저 들어옴: 긴급 아님
        assertThat(saveVulnerability(SourceCode.GHSA_HIGH, cveId, "https://github.com/advisories/GHSA-hhhh-iiii-jjjj", "8.8"))
                .isEqualTo(SaveOutcome.CREATED);
        assertThat(findByCveId(cveId).isUrgent()).isFalse();
        assertThat(articleRepository.findAlertCandidates(now, Limit.of(50))).filteredOn(Article::isUrgent).isEmpty();

        // 같은 권고를 high 출처가 다시 줌: 변화 없음
        assertThat(saveVulnerability(SourceCode.GHSA_HIGH, cveId, "https://github.com/advisories/GHSA-hhhh-iiii-jjjj", "8.8"))
                .isEqualTo(SaveOutcome.DUPLICATE);

        // critical 로 재평가되어 GHSA 출처에서 들어옴: 긴급만 올라가고 행은 그대로
        assertThat(saveVulnerability(SourceCode.GHSA, cveId, "https://github.com/advisories/GHSA-hhhh-iiii-jjjj", "9.8"))
                .isEqualTo(SaveOutcome.MERGED);
        assertThat(findByCveId(cveId).isUrgent()).isTrue();
        assertThat(findByCveId(cveId).getSource().getCode()).isEqualTo(SourceCode.GHSA_HIGH);

        // NVD 가 들어오면 링크·출처·점수는 NVD 것으로, 긴급은 유지 (NVD 점수가 낮아도)
        assertThat(saveVulnerability(SourceCode.NVD, cveId, "https://nvd.nist.gov/vuln/detail/" + cveId, "8.1"))
                .isEqualTo(SaveOutcome.MERGED);
        Article merged = findByCveId(cveId);
        assertThat(articleRepository.count()).isEqualTo(1);
        assertThat(merged.getSource().getCode()).isEqualTo(SourceCode.NVD);
        assertThat(merged.getOriginalUrl()).isEqualTo("https://nvd.nist.gov/vuln/detail/" + cveId);
        assertThat(merged.getCvssScore()).isEqualByComparingTo("8.1");
        assertThat(merged.isUrgent()).isTrue();
    }

    @Test
    void ghsaHighAfterNvdKeepsNvdRowAndStaysNotUrgent() {
        String cveId = "CVE-2026-8802";
        saveVulnerability(SourceCode.NVD, cveId, "https://nvd.nist.gov/vuln/detail/" + cveId, "7.5");

        assertThat(saveVulnerability(SourceCode.GHSA_HIGH, cveId, "https://github.com/advisories/GHSA-kkkk-llll-mmmm", "8.7"))
                .isEqualTo(SaveOutcome.DUPLICATE);
        Article article = findByCveId(cveId);
        assertThat(article.getSource().getCode()).isEqualTo(SourceCode.NVD);
        assertThat(article.isUrgent()).isFalse();
        assertThat(article.getCvssScore()).isEqualByComparingTo("7.5");
    }

    private Article saveAdvisory(String sourceCode, String title, Duration publishedBefore) {
        Source source = findSource(sourceCode);
        Instant publishedAt = now.minus(publishedBefore);
        boolean isUrgent = ArticleUrgencyPolicy.isUrgent(sourceCode, title, null, false);
        return articleRepository.save(Article.create(source, new ArticleDraft(source.getKind(), source.getRegion(), title,
                "https://example.test/" + sourceCode + "/" + System.nanoTime(), source.getDisplayName(),
                publishedAt, false, null, null, null, false, isUrgent), publishedAt));
    }

    private SaveOutcome saveVulnerability(String sourceCode, String cveId, String originalUrl, String cvssScore) {
        Source source = findSource(sourceCode);
        BigDecimal score = new BigDecimal(cvssScore);
        String title = "Example Parser: denial of service in nested input";
        return articleSaveService.saveOrMerge(source, new ArticleDraft(source.getKind(), source.getRegion(), title,
                originalUrl, source.getDisplayName(), now, false, cveId, score, "3.1", false,
                ArticleUrgencyPolicy.isUrgent(sourceCode, title, score, false)), now);
    }

    private List<ArticleResponse> findItems(ArticleKind kind, ArticleRegion region, String sourceCode) {
        return articleQueryService.findArticles(
                new ArticleSearchCondition(kind, region, null, null, sourceCode, null, null, null)).items();
    }

    private Article findByCveId(String cveId) {
        return articleRepository.findByCveId(cveId).orElseThrow();
    }

    private Source findSource(String sourceCode) {
        return sourceRepository.findByCode(sourceCode).orElseThrow();
    }
}
