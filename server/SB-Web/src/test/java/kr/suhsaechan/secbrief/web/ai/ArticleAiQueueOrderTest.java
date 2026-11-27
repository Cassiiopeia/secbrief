package kr.suhsaechan.secbrief.web.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.web.support.ArticleTestWriter;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.ActiveProfiles;

/**
 * AI 제목 분석 대기열 (DB): 게시일 최신순 (같으면 id 큰 것), 처리 끝난 기사는 빠짐, limit 건만 읽음
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ArticleTestWriter.class)
class ArticleAiQueueOrderTest extends PostgresContainerTestSupport {

    private static final Instant NOW = Instant.parse("2026-11-26T03:00:00Z");

    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private ArticleTestWriter articleTestWriter;

    @BeforeEach
    void clearArticles() {
        articleRepository.deleteAll();
    }

    @Test
    void readsNewestPublishedArticleFirst() {
        // 먼저 수집한(= id 가 작은) 기사가 더 새 소식일 수 있음: id 가 아니라 게시일로 정렬
        Article newestArticle = articleTestWriter.saveNews(NOW, ArticleTopic.GENERAL);
        Article oldestArticle = articleTestWriter.saveNews(NOW.minus(Duration.ofDays(3)), ArticleTopic.GENERAL);
        Article middleArticle = articleTestWriter.saveNews(NOW.minus(Duration.ofHours(2)), ArticleTopic.GENERAL);

        List<Article> pendingArticles =
                articleRepository.findByAiAnalysisState_StatusOrderByPublishedAtDescIdDesc(AiStatus.PENDING, Limit.of(20));

        assertThat(pendingArticles).extracting(Article::getId)
                .containsExactly(newestArticle.getId(), middleArticle.getId(), oldestArticle.getId());
    }

    @Test
    void breaksPublishedAtTieWithLargerIdFirst() {
        Article firstSavedArticle = articleTestWriter.saveNews(NOW, ArticleTopic.GENERAL);
        Article secondSavedArticle = articleTestWriter.saveNews(NOW, ArticleTopic.GENERAL);

        assertThat(articleRepository.findByAiAnalysisState_StatusOrderByPublishedAtDescIdDesc(AiStatus.PENDING, Limit.of(20)))
                .extracting(Article::getId).containsExactly(secondSavedArticle.getId(), firstSavedArticle.getId());
    }

    @Test
    void readsOnlyPendingArticlesUpToLimit() {
        Article newestArticle = articleTestWriter.saveNews(NOW, ArticleTopic.GENERAL);
        // 분석이 끝난 기사 (주제가 정해짐): 대기열에서 빠짐
        articleTestWriter.saveNews(NOW.minus(Duration.ofMinutes(1)), ArticleTopic.RANSOMWARE);
        articleTestWriter.saveNews(NOW.minus(Duration.ofHours(1)), ArticleTopic.GENERAL);
        articleTestWriter.saveNews(NOW.minus(Duration.ofHours(2)), ArticleTopic.GENERAL);

        assertThat(articleRepository.findByAiAnalysisState_StatusOrderByPublishedAtDescIdDesc(AiStatus.PENDING, Limit.of(20))).hasSize(3);
        assertThat(articleRepository.findByAiAnalysisState_StatusOrderByPublishedAtDescIdDesc(AiStatus.PENDING, Limit.of(1)))
                .extracting(Article::getId).containsExactly(newestArticle.getId());
    }
}
