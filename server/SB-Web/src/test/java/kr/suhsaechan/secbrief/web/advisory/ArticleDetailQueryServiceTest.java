package kr.suhsaechan.secbrief.web.advisory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.dto.ArticleDetailResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.advisory.dto.SourceFactResponse;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.policy.RelatedArticlePolicy;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.advisory.service.ArticleDetailQueryService;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 기사 상세 (DB): 요약 있음·없음, 사실 값, 없는 id, 관련 기사 순서(같은 CVE > 같은 회사·제품 > 겹치는 키워드 수 > 같은 주제, 그 안에서 최신순)
 * 관련 기사는 자기 자신·조회 기간 밖 제외, 최대 6건
 */
@SpringBootTest
@ActiveProfiles("test")
class ArticleDetailQueryServiceTest extends PostgresContainerTestSupport {

    private static final String SOURCE_TEXT = "Attackers are exploiting a flaw in Example VPN to steal session tokens.";

    @Autowired
    private ArticleDetailQueryService articleDetailQueryService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private SourceRepository sourceRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Source source;
    // 조회 기간 판정이 실제 시계 기준이라 지금 시각에서 출발
    private final Instant now = Instant.now();
    private int savedCount;

    @BeforeEach
    void clearArticles() {
        articleRepository.deleteAll();
        source = sourceRepository.findByCode(SourceCode.THE_HACKER_NEWS).orElseThrow();
    }

    @Test
    void returnsSummaryKeyPointsActionGuideAndFacts() {
        Article article = Article.create(source, createDraft("Example VPN flaw exploited", now, SOURCE_TEXT,
                List.of(new SourceFact("조치 기한", "2026-12-11"))), now);
        article.getSummaryState().applySummary("Example VPN 결함이 악용되고 있어요.", List.of("세션 토큰을 노려요."), "패치를 적용하세요.");
        Long articleId = articleRepository.save(article).getId();

        ArticleDetailResponse detail = articleDetailQueryService.findArticleDetail(articleId);

        assertThat(detail.article().id()).isEqualTo(articleId);
        assertThat(detail.article().title()).isEqualTo("Example VPN flaw exploited");
        assertThat(detail.isAiSummarized()).isTrue();
        assertThat(detail.summary()).isEqualTo("Example VPN 결함이 악용되고 있어요.");
        assertThat(detail.keyPoints()).containsExactly("세션 토큰을 노려요.");
        assertThat(detail.actionGuide()).isEqualTo("패치를 적용하세요.");
        assertThat(detail.facts()).containsExactly(new SourceFactResponse("조치 기한", "2026-12-11"));
        assertThat(detail.relatedItems()).isEmpty();
    }

    @Test
    void opensWithoutSummaryWhenPendingFailedOrMissing() {
        Long pendingId = saveArticle("Pending article", now, SOURCE_TEXT);
        Long noneId = saveArticle("Article without source text", now, null);
        Article failedArticle = Article.create(source, createDraft("Failed article", now, SOURCE_TEXT, List.of()), now);
        failedArticle.getSummaryState().recordFailure();
        failedArticle.getSummaryState().recordFailure();
        failedArticle.getSummaryState().recordFailure();
        Long failedId = articleRepository.save(failedArticle).getId();

        assertThat(List.of(pendingId, noneId, failedId)).allSatisfy(articleId -> {
            ArticleDetailResponse detail = articleDetailQueryService.findArticleDetail(articleId);
            assertThat(detail.isAiSummarized()).isFalse();
            assertThat(detail.summary()).isNull();
            assertThat(detail.keyPoints()).isEmpty();
            assertThat(detail.actionGuide()).isNull();
            assertThat(detail.facts()).isEmpty();
            assertThat(detail.article().originalUrl()).startsWith("https://");
        });
    }

    @Test
    void throwsResourceNotFoundForUnknownId() {
        assertThatThrownBy(() -> articleDetailQueryService.findArticleDetail(987_654_321L))
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void ordersRelatedItemsByCveThenSubjectThenSharedKeywordsThenTopic() {
        Long targetId = saveArticle("Example VPN patches CVE-2026-1111 under attack", now, null);
        updateAnalysis(targetId, "Example VPN", "REMOTE_ACCESS");
        addKeywords(targetId, "vpn", "zero-day", "session");

        Long sameTopicId = saveArticle("Another gateway advisory", now.minus(Duration.ofHours(1)), null);
        updateAnalysis(sameTopicId, null, "REMOTE_ACCESS");
        Long oneKeywordId = saveArticle("Session hijacking campaign", now.minus(Duration.ofHours(2)), null);
        addKeywords(oneKeywordId, "session", "phishing");
        Long twoKeywordsOlderId = saveArticle("VPN zero-day roundup", now.minus(Duration.ofHours(4)), null);
        addKeywords(twoKeywordsOlderId, "vpn", "zero-day");
        Long twoKeywordsNewerId = saveArticle("Zero-day in another VPN", now.minus(Duration.ofHours(3)), null);
        addKeywords(twoKeywordsNewerId, "zero-day", "vpn");
        Long sameSubjectId = saveArticle("Vendor ships emergency update", now.minus(Duration.ofHours(5)), null);
        updateAnalysis(sameSubjectId, "example vpn", "VULNERABILITY");
        Long sameCveId = saveArticle("Researchers detail cve-2026-1111 exploit chain", now.minus(Duration.ofHours(6)), null);
        // 제외 대상: 뒤에 숫자가 이어지는 다른 CVE, 관련 없는 기사, 조회 기간 밖의 같은 CVE
        saveArticle("Unrelated CVE-2026-11112 notice", now.minus(Duration.ofHours(1)), null);
        saveArticle("CVE-2026-1111 first reported long ago",
                now.minus(RelatedArticlePolicy.RELATED_ARTICLE_LOOKBACK).minus(Duration.ofDays(1)), null);

        ArticleDetailResponse detail = articleDetailQueryService.findArticleDetail(targetId);

        assertThat(detail.relatedItems()).extracting(ArticleResponse::id)
                .containsExactly(sameCveId, sameSubjectId, twoKeywordsNewerId, twoKeywordsOlderId, oneKeywordId, sameTopicId);
    }

    @Test
    void limitsRelatedItemsAndFindsCveArticleFromNewsTitle() {
        Long cveArticleId = articleRepository.save(Article.create(source, new ArticleDraft(ArticleKind.VULNERABILITY,
                ArticleRegion.OVERSEAS, "CVE-2026-2222: Example Server deserialization", "https://nvd.nist.gov/vuln/detail/CVE-2026-2222",
                "NVD", now.minus(Duration.ofDays(2)), false, "CVE-2026-2222", null, null, false, false), now)).getId();
        Long newsId = saveArticle("Attackers exploit CVE-2026-2222 in Example Server", now, null);
        for (int index = 0; index < RelatedArticlePolicy.MAXIMUM_RELATED_ARTICLE_COUNT + 2; index++) {
            saveArticle("More attacks abuse CVE-2026-2222 wave " + index, now.minus(Duration.ofMinutes(index + 1)), null);
        }
        saveArticle("General news without any link", now.minus(Duration.ofSeconds(30)), null);

        List<ArticleResponse> relatedItems = articleDetailQueryService.findArticleDetail(newsId).relatedItems();

        // 같은 CVE 를 다룬 기사만 최신순 6건까지 (주제가 GENERAL 이면 같은 주제로 묶지 않아 무관한 최신 기사는 없음)
        assertThat(relatedItems).hasSize(RelatedArticlePolicy.MAXIMUM_RELATED_ARTICLE_COUNT);
        assertThat(relatedItems).extracting(ArticleResponse::id).doesNotContain(newsId);
        assertThat(relatedItems).extracting(ArticleResponse::title).allMatch(title -> title.contains("CVE-2026-2222"));
        assertThat(relatedItems.get(0).title()).isEqualTo("More attacks abuse CVE-2026-2222 wave 0");
        assertThat(cveArticleId).isNotNull();
    }

    @Test
    void returnsNoRelatedItemsForUnclassifiedArticleWithoutCveSubjectOrKeywords() {
        Long newsId = saveArticle("Plain security news", now, null);
        saveArticle("Another unrelated news", now.minus(Duration.ofMinutes(1)), null);

        assertThat(articleDetailQueryService.findArticleDetail(newsId).relatedItems()).isEmpty();
    }

    private Long saveArticle(String title, Instant publishedAt, String sourceText) {
        return articleRepository.save(Article.create(source, createDraft(title, publishedAt, sourceText, List.of()), now)).getId();
    }

    private ArticleDraft createDraft(String title, Instant publishedAt, String sourceText, List<SourceFact> sourceFacts) {
        savedCount++;
        return new ArticleDraft(ArticleKind.NEWS, ArticleRegion.OVERSEAS, title,
                "https://thehackernews.com/2026/11/detail-" + savedCount + ".html", "The Hacker News", publishedAt, false,
                null, null, null, false, false, null, sourceText, sourceFacts);
    }

    // AI 제목 분석 결과 자리 (회사·제품 이름, 주제)를 열에 직접 채움
    private void updateAnalysis(Long articleId, String subjectName, String topic) {
        jdbcTemplate.update("update article set subject_name = ?, topic = ? where id = ?", subjectName, topic, articleId);
    }

    private void addKeywords(Long articleId, String... keywords) {
        for (String keyword : keywords) {
            jdbcTemplate.update("insert into article_keyword (article_id, keyword) values (?, ?)", articleId, keyword);
        }
    }
}
