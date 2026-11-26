package kr.suhsaechan.secbrief.web.advisory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.classification.ArticleClassificationService;
import kr.suhsaechan.secbrief.advisory.classification.ClassificationBatchResult;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.ClassificationSource;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 기존 기사 채우기 (DB): 분류 전 행을 규칙으로 분류, 규칙이 못 정한 예전 AI 완료 행만 AI 분야 고르기로 1번 돌림,
 * 다시 돌려도 같은 결과 (분류한 행은 고르지 않음), 다시 분류 요청은 알림 처리 시각을 건드리지 않음
 */
@SpringBootTest
@ActiveProfiles("test")
class ArticleClassificationBackfillTest extends PostgresContainerTestSupport {

    @Autowired
    private ArticleClassificationService articleClassificationService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private SourceRepository sourceRepository;

    @BeforeEach
    void clearArticles() {
        articleRepository.deleteAll();
    }

    @Test
    void classifiesExistingRowsOnceAndSendsOnlyUnmatchedOnesToAi() {
        Article fortinet = saveAiDoneArticle(SourceCode.NVD, "CVE-2026-9501: Fortinet FortiOS heap overflow");
        Article redHat = saveAiDoneArticle(SourceCode.REDHAT_RHSA, "RHSA-2026:1: Important: tigervnc security update");
        Article unmatched = saveAiDoneArticle(SourceCode.NVD, "CVE-2026-9503: Thinkwise buffer overflow");
        Article alertLevel = saveAiDoneArticle(SourceCode.KISA_ALERT_LEVEL, "사이버위기 경보 단계 '관심' → '주의' 상향 조정");

        ClassificationBatchResult first = articleClassificationService.classifyPendingBatch();
        ClassificationBatchResult second = articleClassificationService.classifyPendingBatch();

        assertThat(first.targetCount()).isEqualTo(4);
        assertThat(first.countOf(ClassificationSource.PRODUCT)).isEqualTo(1);
        assertThat(first.countOf(ClassificationSource.KEYWORD)).isEqualTo(1);
        assertThat(first.aiRequestedCount()).isEqualTo(1);
        // 두 번째: 모두 분류됨 (AI 대기로 돌린 행도 일단 other) > 대상 0
        assertThat(second.targetCount()).isZero();
        assertThat(find(fortinet)).satisfies(article -> {
            assertThat(article.getFieldCode()).isEqualTo("network");
            assertThat(article.getProductCode()).isEqualTo("fortinet");
            assertThat(article.isNotable()).isTrue();
            assertThat(article.getClassifiedAt()).isNotNull();
        });
        assertThat(find(redHat).getFieldCode()).isEqualTo("os");
        assertThat(find(unmatched).getAiStatus()).isEqualTo(AiStatus.PENDING);
        assertThat(find(unmatched).getFieldCode()).isEqualTo("other");
        assertThat(find(unmatched).getClassifiedAt()).isNotNull();
        assertThat(find(alertLevel).getFieldCode()).isNull();
        assertThat(find(alertLevel).getFieldSource()).isEqualTo(ClassificationSource.NONE);
    }

    @Test
    void reclassifyRequestKeepsAlertProcessingAndDoesNotAskAiAgain() {
        Article unmatched = saveAiDoneArticle(SourceCode.NVD, "CVE-2026-9504: Thinkwise buffer overflow");
        unmatched.changeAiFieldCode(null);
        articleClassificationService.classify(unmatched);
        articleRepository.save(unmatched);
        Instant processedAt = find(unmatched).getUrgentNotifiedAt();

        articleClassificationService.requestReclassificationOfAll();
        ClassificationBatchResult reclassifyBatch = articleClassificationService.classifyPendingBatch();

        // 한 번 분류한 행은 AI 로 다시 돌리지 않고 그 밖으로
        assertThat(reclassifyBatch.aiRequestedCount()).isZero();
        assertThat(find(unmatched).getFieldCode()).isEqualTo("other");
        assertThat(find(unmatched).getUrgentNotifiedAt()).isEqualTo(processedAt);
    }

    private Article saveAiDoneArticle(String sourceCode, String title) {
        Source source = sourceRepository.findByCode(sourceCode).orElseThrow();
        Instant now = Instant.now();
        Article article = Article.create(source, new ArticleDraft(source.getKind(), source.getRegion(), title,
                "https://example.test/" + System.nanoTime(), source.getDisplayName(), now, false, null,
                source.getKind() == ArticleKind.VULNERABILITY ? new BigDecimal("9.8") : null, null, false, false), now);
        article.applyAiAnalysis(ArticleTopic.VULNERABILITY, null, ArticleImportance.NOTABLE, null);
        return articleRepository.save(article);
    }

    private Article find(Article article) {
        return articleRepository.findById(article.getId()).orElseThrow();
    }
}
