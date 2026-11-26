package kr.suhsaechan.secbrief.web.advisory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSearchCondition;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.service.ArticleQueryService;
import kr.suhsaechan.secbrief.advisory.service.TodayQueryService;
import kr.suhsaechan.secbrief.web.support.ArticleTestWriter;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 중요도 조회 (DB): 오늘의 주요 소식(72시간 안, 높은 순 > 최신순, 6건), 최소 중요도·출처 코드 필터, 응답의 출처 코드
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ArticleTestWriter.class)
class ArticleImportanceQueryTest extends PostgresContainerTestSupport {

    @Autowired
    private TodayQueryService todayQueryService;
    @Autowired
    private ArticleQueryService articleQueryService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private ArticleTestWriter articleTestWriter;

    private Instant now;

    @BeforeEach
    void clearArticles() {
        articleRepository.deleteAll();
        now = Instant.now();
    }

    @Test
    void todayHeadlinesAreMostImportantFirstThenNewestWithinThreeDays() {
        Article olderHeadline = saveNews(now.minus(Duration.ofHours(30)), ArticleImportance.HEADLINE);
        Article newerNotable = saveNews(now.minus(Duration.ofHours(1)), ArticleImportance.NOTABLE);
        Article newerHeadline = saveNews(now.minus(Duration.ofHours(2)), ArticleImportance.HEADLINE);
        saveNews(now.minus(Duration.ofMinutes(5)), ArticleImportance.ROUTINE);
        saveNews(now.minus(Duration.ofHours(80)), ArticleImportance.HEADLINE);

        List<ArticleResponse> headlineItems = todayQueryService.findToday().headlineItems();

        assertThat(headlineItems).extracting(ArticleResponse::id)
                .containsExactly(newerHeadline.getId(), olderHeadline.getId(), newerNotable.getId());
    }

    @Test
    void todayHeadlinesAreLimitedToSix() {
        for (int hoursAgo = 1; hoursAgo <= 8; hoursAgo++) {
            saveNews(now.minus(Duration.ofHours(hoursAgo)), ArticleImportance.NOTABLE);
        }

        assertThat(todayQueryService.findToday().headlineItems()).hasSize(TodayQueryService.TODAY_HEADLINE_SIZE);
    }

    @Test
    void filtersByMinimumImportanceAndSourceCode() {
        saveNews(now.minus(Duration.ofHours(1)), ArticleImportance.HEADLINE);
        saveNews(now.minus(Duration.ofHours(2)), ArticleImportance.NOTABLE);
        saveNews(now.minus(Duration.ofHours(3)), ArticleImportance.ROUTINE);
        articleTestWriter.saveArticle(now, ArticleKind.ADVISORY, ArticleRegion.DOMESTIC, ArticleTopic.GENERAL, false);

        assertThat(findItems(ArticleImportance.NOTABLE, null)).extracting(ArticleResponse::importance)
                .containsExactly(ArticleImportance.HEADLINE, ArticleImportance.NOTABLE);
        assertThat(findItems(ArticleImportance.HEADLINE, null)).hasSize(1);
        assertThat(findItems(null, SourceCode.KISA)).extracting(ArticleResponse::sourceCode).containsExactly(SourceCode.KISA);
        assertThat(findItems(null, null)).hasSize(4);
    }

    private List<ArticleResponse> findItems(ArticleImportance minimumImportance, String sourceCode) {
        return articleQueryService.findArticles(
                new ArticleSearchCondition(null, null, null, minimumImportance, sourceCode, null, null, null)).items();
    }

    private Article saveNews(Instant publishedAt, ArticleImportance importance) {
        Article article = articleTestWriter.saveNews(publishedAt, ArticleTopic.GENERAL);
        article.applyAiAnalysis(ArticleTopic.GENERAL, null, importance, null);
        return articleRepository.save(article);
    }
}
