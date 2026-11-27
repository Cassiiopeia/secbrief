package kr.suhsaechan.secbrief.web.advisory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.NewArticleTagRow;
import kr.suhsaechan.secbrief.web.support.ArticleTestWriter;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * AI 분석 값 조회 경로 고정 (DB): 새 소식 분류 행의 회사·제품 이름, AI 분석 열 이름 그대로, AI 대기열
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ArticleTestWriter.class)
class ArticleAiAnalysisQueryTest extends PostgresContainerTestSupport {

    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private ArticleTestWriter articleTestWriter;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Instant now;

    @BeforeEach
    void clearArticles() {
        articleRepository.deleteAll();
        now = Instant.now();
    }

    @Test
    void newArticleTagRowsCarrySubjectName() {
        Article article = articleTestWriter.saveNews(now.minus(Duration.ofHours(1)), ArticleTopic.GENERAL);
        article.applyAiAnalysis(ArticleTopic.VULNERABILITY, "번역 제목", ArticleImportance.ROUTINE, "Ivanti");
        articleRepository.save(article);

        assertThat(articleRepository.findNewArticleTagRows(now.minus(Duration.ofDays(1))))
                .extracting(NewArticleTagRow::subjectName).containsExactly("Ivanti");
    }

    @Test
    void storesAiAnalysisInSameColumnsAndQueuesByStatus() {
        Article done = articleTestWriter.saveNews(now.minus(Duration.ofHours(1)), ArticleTopic.GENERAL);
        done.applyAiAnalysis(ArticleTopic.PRIVACY, "번역 제목", ArticleImportance.NOTABLE, "Example");
        articleRepository.save(done);
        Article failedOnce = articleTestWriter.saveNews(now.minus(Duration.ofHours(2)), ArticleTopic.GENERAL);
        failedOnce.recordAiFailure();
        articleRepository.save(failedOnce);

        assertThat(jdbcTemplate.queryForMap(
                "select topic, translated_title, subject_name, ai_status, ai_attempt_count from article where id = ?",
                done.getId()))
                .containsEntry("topic", "PRIVACY").containsEntry("translated_title", "번역 제목")
                .containsEntry("subject_name", "Example").containsEntry("ai_status", "DONE")
                .containsEntry("ai_attempt_count", 0);
        assertThat(jdbcTemplate.queryForObject("select ai_attempt_count from article where id = ?",
                Integer.class, failedOnce.getId())).isEqualTo(1);
        assertThat(articleRepository.findByAiAnalysisState_StatusOrderByPublishedAtDescIdDesc(AiStatus.PENDING, Limit.of(20)))
                .extracting(Article::getId).containsExactly(failedOnce.getId());
    }
}
