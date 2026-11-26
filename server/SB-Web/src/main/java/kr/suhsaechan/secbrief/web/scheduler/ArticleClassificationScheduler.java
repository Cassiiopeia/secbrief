package kr.suhsaechan.secbrief.web.scheduler;

import java.util.EnumMap;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.classification.ArticleClassificationService;
import kr.suhsaechan.secbrief.advisory.classification.ClassificationBatchResult;
import kr.suhsaechan.secbrief.advisory.constant.ClassificationSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 분류 틱: 1분마다 분류 대기 행을 200건씩 최대 batches-per-tick 번 (AI 대기 기한 초과, 기존 행 백필, 관리자 다시 분류)
 * 분류한 행은 다시 고르지 않아 여러 번 돌아도 같음, 대상이 있던 틱만 근거별 건수를 남김
 * secbrief.classification.enabled=false 면 빈을 만들지 않음 (테스트)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "secbrief.classification.enabled", havingValue = "true", matchIfMissing = true)
public class ArticleClassificationScheduler {

    private final ArticleClassificationService articleClassificationService;

    @Value("${secbrief.classification.batches-per-tick}")
    private int batchesPerTick;

    @Scheduled(fixedDelayString = "${secbrief.classification.tick-milliseconds}",
            initialDelayString = "${secbrief.classification.initial-delay-milliseconds}")
    public void classifyPendingArticles() {
        int targetCount = 0;
        int aiRequestedCount = 0;
        Map<ClassificationSource, Integer> countBySource = new EnumMap<>(ClassificationSource.class);
        for (int batchIndex = 0; batchIndex < batchesPerTick; batchIndex++) {
            ClassificationBatchResult result = articleClassificationService.classifyPendingBatch();
            targetCount += result.targetCount();
            aiRequestedCount += result.aiRequestedCount();
            result.countBySource().forEach((source, count) -> countBySource.merge(source, count, Integer::sum));
            if (result.targetCount() < ArticleClassificationService.BATCH_SIZE) {
                break;
            }
        }
        if (targetCount > 0) {
            log.info("[article classify] classified : targets={}, byProduct={}, byKeyword={}, byAi={}, other={}, aiRequested={}",
                    targetCount, countBySource.getOrDefault(ClassificationSource.PRODUCT, 0),
                    countBySource.getOrDefault(ClassificationSource.KEYWORD, 0),
                    countBySource.getOrDefault(ClassificationSource.AI, 0),
                    countBySource.getOrDefault(ClassificationSource.NONE, 0), aiRequestedCount);
        }
    }
}
