package kr.suhsaechan.secbrief.advisory.classification;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalog;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalogProvider;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.constant.ClassificationSource;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기사 분류 유스케이스: 1건 분류(AI 결과 기록 때) / 대기 묶음 분류(AI 대기 기한 초과·백필·다시 분류)
 * 묶음에서 규칙으로 분야를 못 정했고 AI 분야도 없는 예전 행은 AI 대기로 돌려 제목 분석이 분야를 고르게 함 (1번만)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleClassificationService {

    // 한 번에 분류하는 행 수
    public static final int BATCH_SIZE = 200;

    private final ArticleRepository articleRepository;
    private final TagCatalogProvider tagCatalogProvider;
    private final Clock clock;

    /** 1건 분류 (호출부 트랜잭션 안, 엔티티를 고침) */
    public void classify(Article article) {
        TagCatalog catalog = tagCatalogProvider.getCatalog();
        apply(article, ArticleClassifier.classify(ArticleClassificationInput.from(article), catalog), catalog);
    }

    /**
     * 대기 묶음 1회 (최대 200건): 분류 > 근거별 건수, 분야가 필요한 예전 행은 AI 대기로
     */
    @Transactional
    public ClassificationBatchResult classifyPendingBatch() {
        Instant holdDeadline = clock.instant().minus(Article.AI_JUDGEMENT_HOLD_DURATION);
        List<Article> targets = articleRepository.findClassificationTargets(holdDeadline, Limit.of(BATCH_SIZE));
        TagCatalog catalog = tagCatalogProvider.getCatalog();
        Map<ClassificationSource, Integer> countBySource = new EnumMap<>(ClassificationSource.class);
        int aiRequestedCount = 0;
        for (Article article : targets) {
            ArticleClassification classification =
                    ArticleClassifier.classify(ArticleClassificationInput.from(article), catalog);
            if (isAiFieldNeeded(article, classification)) {
                article.requestAiFieldAnalysis();
                aiRequestedCount++;
                continue;
            }
            apply(article, classification, catalog);
            countBySource.merge(classification.fieldSource(), 1, Integer::sum);
        }
        return new ClassificationBatchResult(targets.size(), countBySource, aiRequestedCount);
    }

    /** 전체 다시 분류 요청 (다음 묶음부터 차례로, 알림은 다시 보내지 않음) */
    @Transactional
    public int requestReclassificationOfAll() {
        int requestedCount = articleRepository.requestReclassificationOfAll();
        log.info("[article classify] reclassification requested : articles={}", requestedCount);
        return requestedCount;
    }

    // 규칙 실패 + AI 분야 없음 + AI 를 이미 마친 + 한 번도 분류한 적 없는 행 (분야 열이 생기기 전 행)
    private static boolean isAiFieldNeeded(Article article, ArticleClassification classification) {
        return !ArticleClassifier.hasRuleField(classification)
                && classification.fieldCode() != null
                && article.getAiFieldCode() == null
                && article.getAiStatus() == AiStatus.DONE
                && article.getFieldSource() == null;
    }

    private void apply(Article article, ArticleClassification classification, TagCatalog catalog) {
        boolean isNotable = ArticleNotablePolicy.isNotable(article.isKev(), classification.productCode(),
                article.getCvssScore(), article.getAiStatus(), article.getImportance(), catalog);
        article.applyClassification(classification, isNotable, clock.instant());
    }
}
