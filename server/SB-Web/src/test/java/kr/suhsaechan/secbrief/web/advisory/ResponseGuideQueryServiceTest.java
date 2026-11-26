package kr.suhsaechan.secbrief.web.advisory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ResponseGuideMatchReason;
import kr.suhsaechan.secbrief.advisory.constant.ResponseGuideSection;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.dto.ArticleDetailResponse;
import kr.suhsaechan.secbrief.advisory.dto.ResponseGuideItemResponse;
import kr.suhsaechan.secbrief.advisory.dto.ResponseGuideSectionResponse;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;
import kr.suhsaechan.secbrief.advisory.fact.SourceFactLabel;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.policy.ResponseGuidePolicy;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.advisory.service.ArticleDetailQueryService;
import kr.suhsaechan.secbrief.advisory.service.ResponseGuideQueryService;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 대응 모아보기 (DB): 출처별 구역 판정(시드 출처 전체), 같은 CVE 묶기(1년, 뒤에 숫자 붙은 번호 제외),
 * 제품 이름 묶기(±14일, 이름 정리), KISA 제조사 공지의 앞 낱말 묶기, 잘못 묶일 경우 막기(행사 주제, 다른 CVE 를 다룬 글, 일반 기사 앞 낱말), 구역별 5건
 */
@SpringBootTest
@ActiveProfiles("test")
class ResponseGuideQueryServiceTest extends PostgresContainerTestSupport {

    private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");

    @Autowired
    private ResponseGuideQueryService responseGuideQueryService;
    @Autowired
    private ArticleDetailQueryService articleDetailQueryService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private SourceRepository sourceRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int savedCount;

    @BeforeEach
    void clearArticles() {
        articleRepository.deleteAll();
    }

    @Test
    void classifiesEverySeededSourceIntoOneSectionExceptAlertLevel() {
        Map<String, Optional<ResponseGuideSection>> sectionBySourceCode = sourceRepository.findAll().stream()
                .collect(Collectors.toMap(Source::getCode,
                        source -> ResponseGuidePolicy.toSection(source.getCode(), source.getKind())));

        assertThat(sectionBySourceCode).containsAllEntriesOf(Map.ofEntries(
                Map.entry(SourceCode.KISA, Optional.of(ResponseGuideSection.OFFICIAL)),
                Map.entry(SourceCode.KISA_VULNERABILITY, Optional.of(ResponseGuideSection.OFFICIAL)),
                Map.entry(SourceCode.KISA_REPORT, Optional.of(ResponseGuideSection.OFFICIAL)),
                Map.entry(SourceCode.UBUNTU_USN, Optional.of(ResponseGuideSection.OFFICIAL)),
                Map.entry(SourceCode.DEBIAN_DSA, Optional.of(ResponseGuideSection.OFFICIAL)),
                Map.entry(SourceCode.REDHAT_RHSA, Optional.of(ResponseGuideSection.OFFICIAL)),
                Map.entry(SourceCode.NODEJS_SECURITY, Optional.of(ResponseGuideSection.OFFICIAL)),
                Map.entry(SourceCode.NVD, Optional.of(ResponseGuideSection.FACT)),
                Map.entry(SourceCode.CISA_KEV, Optional.of(ResponseGuideSection.FACT)),
                Map.entry(SourceCode.GHSA, Optional.of(ResponseGuideSection.FACT)),
                Map.entry(SourceCode.GHSA_HIGH, Optional.of(ResponseGuideSection.FACT)),
                Map.entry(SourceCode.DAILYSECU, Optional.of(ResponseGuideSection.NEWS)),
                Map.entry(SourceCode.THE_HACKER_NEWS, Optional.of(ResponseGuideSection.NEWS)),
                Map.entry(SourceCode.BLEEPING_COMPUTER, Optional.of(ResponseGuideSection.NEWS)),
                Map.entry(SourceCode.KISA_ALERT_LEVEL, Optional.empty())));
    }

    @Test
    void groupsSameCveIntoOfficialFactAndNewsSections() {
        Article news = save(SourceCode.THE_HACKER_NEWS, "Attackers exploit CVE-2026-3001 in Example Gateway", null, NOW);
        Article kisaVulnerability = save(SourceCode.KISA_VULNERABILITY,
                "CVE-2026-3001 | 예시소프트 Example Gateway 원격 코드 실행", null, NOW.minus(Duration.ofDays(2)));
        Article cveEntry = saveCveEntry("CVE-2026-3001", "Example Gateway", NOW.minus(Duration.ofDays(3)), true,
                List.of(new SourceFact(SourceFactLabel.ACTION_DUE_DATE, "2026-12-11")));
        Article otherNews = save(SourceCode.DAILYSECU, "예시 게이트웨이 취약점(cve-2026-3001) 악용 주의", null,
                NOW.minus(Duration.ofHours(5)));
        // 제외: 경보단계 공지, 뒤에 숫자가 이어지는 다른 번호
        save(SourceCode.KISA_ALERT_LEVEL, "사이버 위기 경보 상향 (CVE-2026-3001 관련)", null, NOW);
        save(SourceCode.KISA_VULNERABILITY, "CVE-2026-30012 | 다른 제품 취약점", null, NOW);

        List<ResponseGuideSectionResponse> sections = responseGuideQueryService.findResponseGuideSections(news);

        assertThat(sections).extracting(ResponseGuideSectionResponse::section)
                .containsExactly(ResponseGuideSection.OFFICIAL, ResponseGuideSection.FACT, ResponseGuideSection.NEWS);
        assertThat(sections.get(0).items()).extracting(item -> item.article().id())
                .containsExactly(kisaVulnerability.getId());
        ResponseGuideItemResponse factItem = sections.get(1).items().get(0);
        assertThat(factItem.article().id()).isEqualTo(cveEntry.getId());
        assertThat(factItem.matchReason()).isEqualTo(ResponseGuideMatchReason.SAME_CVE);
        assertThat(factItem.matchedCveId()).isEqualTo("CVE-2026-3001");
        assertThat(factItem.article().isKev()).isTrue();
        assertThat(factItem.actionDueDate()).isEqualTo("2026-12-11");
        assertThat(factItem.article().originalUrl()).startsWith("https://");
        assertThat(sections.get(2).items()).extracting(item -> item.article().id())
                .containsExactly(otherNews.getId());
        assertThat(sections.get(0).items().get(0).sourceName()).isNotBlank();
    }

    @Test
    void looksBackOneYearForSameCveFromTheCveEntry() {
        Article cveEntry = saveCveEntry("CVE-2026-3101", "Example Server", NOW, false, List.of());
        Article recentNotice = save(SourceCode.KISA_VULNERABILITY, "CVE-2026-3101 | Example Server 인증 우회", null,
                NOW.minus(Duration.ofDays(200)));
        Article laterNews = save(SourceCode.BLEEPING_COMPUTER, "CVE-2026-3101 now exploited", null, NOW.plus(Duration.ofDays(60)));
        save(SourceCode.KISA_VULNERABILITY, "CVE-2026-3101 | 1년보다 오래된 공지", null,
                NOW.minus(ResponseGuidePolicy.CVE_MATCH_LOOKBACK).minus(Duration.ofDays(1)));

        List<ResponseGuideSectionResponse> sections = responseGuideQueryService.findResponseGuideSections(cveEntry);

        assertThat(sections).extracting(ResponseGuideSectionResponse::section)
                .containsExactly(ResponseGuideSection.OFFICIAL, ResponseGuideSection.NEWS);
        assertThat(sections.get(0).items()).extracting(item -> item.article().id()).containsExactly(recentNotice.getId());
        assertThat(sections.get(1).items()).extracting(item -> item.article().id()).containsExactly(laterNews.getId());
    }

    @Test
    void linksKisaVendorNoticeByVendorWordAndProductByExactNameWithinWindow() {
        Article cveEntry = saveCveEntry("CVE-2026-4001", "Oracle WebLogic Server", NOW, false, List.of());
        Article kisaNotice = save(SourceCode.KISA, "Oracle 제품 보안 업데이트 권고", "Oracle", NOW.plus(Duration.ofDays(3)));
        Article redHatAdvisory = save(SourceCode.REDHAT_RHSA, "RHSA-2026:1: Important: weblogic security update",
                "oracle  weblogic-server", NOW.minus(Duration.ofDays(1)));
        Article productNews = save(SourceCode.THE_HACKER_NEWS, "WebLogic flaw patched", "Oracle WebLogic Server",
                NOW.minus(Duration.ofDays(2)));
        // 제외: 기간 밖 KISA 공지, 제조사 공지가 아닌 기사의 앞 낱말 일치, 같은 제품의 다른 CVE 항목, 행사 주제, 낱말 경계가 아닌 이름
        save(SourceCode.KISA, "Oracle 제품 보안 업데이트 권고", "Oracle",
                NOW.plus(ResponseGuidePolicy.PRODUCT_MATCH_WINDOW).plus(Duration.ofDays(1)));
        save(SourceCode.DAILYSECU, "오라클 실적 발표", "Oracle", NOW);
        saveCveEntry("CVE-2026-4002", "Oracle WebLogic Server", NOW, true, List.of());
        Article eventNews = save(SourceCode.DAILYSECU, "WebLogic 사용자 행사", "Oracle WebLogic Server", NOW);
        updateTopic(eventNews, "EVENT");
        save(SourceCode.KISA, "OracleX 제품 보안 업데이트 권고", "OracleX", NOW);
        // 제외: 같은 제품 이름이지만 다른 CVE 를 다룬 기사 (다른 취약점)
        save(SourceCode.THE_HACKER_NEWS, "WebLogic CVE-2026-4999 exploited", "Oracle WebLogic Server", NOW);

        List<ResponseGuideSectionResponse> sections = responseGuideQueryService.findResponseGuideSections(cveEntry);

        assertThat(sections).extracting(ResponseGuideSectionResponse::section)
                .containsExactly(ResponseGuideSection.OFFICIAL, ResponseGuideSection.NEWS);
        // 공식 대응: 같은 제품(Red Hat) > 같은 제조사(KISA)
        assertThat(sections.get(0).items()).extracting(item -> item.article().id())
                .containsExactly(redHatAdvisory.getId(), kisaNotice.getId());
        assertThat(sections.get(0).items()).extracting(ResponseGuideItemResponse::matchReason)
                .containsExactly(ResponseGuideMatchReason.SAME_PRODUCT, ResponseGuideMatchReason.SAME_VENDOR);
        assertThat(sections.get(0).items().get(1).matchedCveId()).isNull();
        assertThat(sections.get(1).items()).extracting(item -> item.article().id()).containsExactly(productNews.getId());
    }

    @Test
    void kisaVendorNoticeCollectsVendorCveEntriesKevFirstAndCapsEachSection() {
        Article kisaNotice = save(SourceCode.KISA, "Drupal 제품 보안 업데이트 권고", "Drupal", NOW);
        Article kevEntry = saveCveEntry("CVE-2026-5000", "Drupal Core", NOW.minus(Duration.ofDays(2)), true, List.of());
        for (int index = 1; index <= ResponseGuidePolicy.MAXIMUM_ITEM_COUNT_PER_SECTION + 1; index++) {
            saveCveEntry("CVE-2026-500" + index, "Drupal", NOW.minus(Duration.ofHours(index)), false, List.of());
        }
        save(SourceCode.THE_HACKER_NEWS, "Drupal patches installer flaw", "Drupal", NOW.minus(Duration.ofDays(1)));
        save(SourceCode.THE_HACKER_NEWS, "Drupalgeddon returns", "Drupalgeddon", NOW);

        List<ResponseGuideSectionResponse> sections = responseGuideQueryService.findResponseGuideSections(kisaNotice);

        assertThat(sections).extracting(ResponseGuideSectionResponse::section)
                .containsExactly(ResponseGuideSection.FACT, ResponseGuideSection.NEWS);
        ResponseGuideSectionResponse factSection = sections.get(0);
        assertThat(factSection.totalCount()).isEqualTo(ResponseGuidePolicy.MAXIMUM_ITEM_COUNT_PER_SECTION + 2);
        assertThat(factSection.items()).hasSize(ResponseGuidePolicy.MAXIMUM_ITEM_COUNT_PER_SECTION);
        assertThat(factSection.items().get(0).article().id()).isEqualTo(kevEntry.getId());
        assertThat(factSection.items()).extracting(ResponseGuideItemResponse::matchReason)
                .containsOnly(ResponseGuideMatchReason.SAME_VENDOR);
        assertThat(sections.get(1).totalCount()).isEqualTo(1);
    }

    @Test
    void doesNotLinkEventArticleOrArticleWithoutCveAndSubject() {
        Article eventNews = save(SourceCode.DAILYSECU, "HPE 연례 콘퍼런스", "HPE", NOW);
        updateTopic(eventNews, "EVENT");
        save(SourceCode.KISA, "HPE 제품 보안 업데이트 권고", "HPE", NOW);
        Article plainNews = save(SourceCode.DAILYSECU, "보안 업계 소식", null, NOW);

        assertThat(responseGuideQueryService.findResponseGuideSections(articleRepository.findById(eventNews.getId()).orElseThrow()))
                .isEmpty();
        assertThat(responseGuideQueryService.findResponseGuideSections(plainNews)).isEmpty();
    }

    @Test
    void articleDetailCarriesGuideSectionsAndKeepsRelatedItems() {
        Article news = save(SourceCode.THE_HACKER_NEWS, "Example Router CVE-2026-6001 exploited", null, NOW);
        Article kisaVulnerability = save(SourceCode.KISA_VULNERABILITY, "CVE-2026-6001 | Example Router 인증 우회", null, NOW);

        ArticleDetailResponse detail = articleDetailQueryService.findArticleDetail(news.getId());

        assertThat(detail.responseGuideSections()).hasSize(1);
        assertThat(detail.responseGuideSections().get(0).items().get(0).article().id()).isEqualTo(kisaVulnerability.getId());
        // 이전 앱 빌드용 관련 기사는 그대로 (조회 기간이 실제 시계 기준이라 오래된 시각이면 비어도 됨)
        assertThat(detail.relatedItems()).isNotNull();
    }

    private Article save(String sourceCode, String title, String subjectName, Instant publishedAt) {
        Source source = sourceRepository.findByCode(sourceCode).orElseThrow();
        savedCount++;
        Article article = articleRepository.save(Article.create(source, new ArticleDraft(source.getKind(), ArticleRegion.OVERSEAS,
                title, "https://example.test/guide/" + savedCount, source.getDisplayName(), publishedAt, false, null, null, null,
                false, false), publishedAt));
        updateAnalysis(article, subjectName, "VULNERABILITY");
        return articleRepository.findById(article.getId()).orElseThrow();
    }

    private Article saveCveEntry(String cveId, String subjectName, Instant publishedAt, boolean isKev, List<SourceFact> facts) {
        Source source = sourceRepository.findByCode(isKev ? SourceCode.CISA_KEV : SourceCode.NVD).orElseThrow();
        Article article = articleRepository.save(Article.create(source, new ArticleDraft(source.getKind(), ArticleRegion.OVERSEAS,
                cveId + ": " + subjectName + " flaw", "https://nvd.nist.gov/vuln/detail/" + cveId, "NVD", publishedAt, false,
                cveId, new BigDecimal("9.1"), "3.1", isKev, isKev, null, null, facts), publishedAt));
        updateAnalysis(article, subjectName, "VULNERABILITY");
        return articleRepository.findById(article.getId()).orElseThrow();
    }

    // AI 제목 분석 결과 자리 (회사·제품 이름, 주제)를 열에 직접 채움
    private void updateAnalysis(Article article, String subjectName, String topic) {
        jdbcTemplate.update("update article set subject_name = ?, topic = ? where id = ?", subjectName, topic, article.getId());
    }

    private void updateTopic(Article article, String topic) {
        jdbcTemplate.update("update article set topic = ? where id = ?", topic, article.getId());
    }
}
