package kr.suhsaechan.secbrief.application.ai;

import java.util.List;
import java.util.OptionalInt;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysisOutcome;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysisRequest;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalyzer;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * AI 묶음 처리: 제공자 확인 > 하루 예산 확인 > 대기(PENDING) 기사 최신순 batchSize 건 > requestBatchSize 건씩 묶어 분석 > 건별 성공·실패 기록
 * 건별 격리 (응답에 빠진 건만 실패), 제공자 호출 실패는 시도 횟수를 쓰지 않고 틱을 멈춤, 제목은 로그에 남기지 않음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleAiProcessService {

    private final ArticleTitleAnalyzer articleTitleAnalyzer;
    private final ArticleRepository articleRepository;
    private final ArticleAiResultRecorder articleAiResultRecorder;
    // 한 틱 건수(AI_TITLE_BATCH_SIZE)·호출당 건수(AI_REQUEST_BATCH_SIZE)를 틱마다 읽음 (설정 저장소 > 운영 설정)
    private final SystemConfigService systemConfigService;
    private final AiDailyBudgetGuard aiDailyBudgetGuard;

    public AiProcessResult processPendingArticles() {
        if (!articleTitleAnalyzer.isAvailable()) {
            log.info("[article ai] skipped : provider not configured, provider={}", articleTitleAnalyzer.getProviderCode());
            return new AiProcessResult(0, 0, true);
        }
        if (aiDailyBudgetGuard.isBudgetExhausted()) {
            return new AiProcessResult(0, 0, true);
        }

        int batchSize = systemConfigService.getInt(ConfigKey.AI_TITLE_BATCH_SIZE);
        // 0 이하 값은 1건씩 (나누기 반복이 멈추지 않는 것 방지)
        int requestBatchSize = Math.max(1, systemConfigService.getInt(ConfigKey.AI_REQUEST_BATCH_SIZE));
        List<Article> pendingArticles =
                articleRepository.findByAiStatusOrderByPublishedAtDescIdDesc(AiStatus.PENDING, Limit.of(batchSize));
        int doneCount = 0;
        int failedCount = 0;
        for (int fromIndex = 0; fromIndex < pendingArticles.size(); fromIndex += requestBatchSize) {
            int toIndex = Math.min(fromIndex + requestBatchSize, pendingArticles.size());
            List<Article> batchArticles = pendingArticles.subList(fromIndex, toIndex);
            OptionalInt batchDoneCount = analyzeAndRecordBatch(batchArticles);
            if (batchDoneCount.isEmpty()) {
                // 제공자 장애로 기사가 FAILED 로 굳지 않게 기록 없이 멈춤 (남은 묶음도 같은 이유로 실패)
                break;
            }
            doneCount += batchDoneCount.getAsInt();
            failedCount += batchArticles.size() - batchDoneCount.getAsInt();
        }
        if (doneCount + failedCount > 0) {
            log.info("[article ai] processed : provider={}, done={}, failed={}",
                    articleTitleAnalyzer.getProviderCode(), doneCount, failedCount);
        }
        return new AiProcessResult(doneCount, failedCount, false);
    }

    /**
     * 묶음 1회: 분석 > 건별 기록, 반환은 성공 건수 (제공자 호출 실패면 빈 값: 아무것도 기록하지 않음)
     */
    private OptionalInt analyzeAndRecordBatch(List<Article> articles) {
        List<ArticleTitleAnalysisOutcome> outcomes;
        try {
            outcomes = articleTitleAnalyzer.analyzeBatch(articles.stream()
                    .map(article -> new ArticleTitleAnalysisRequest(article.getTitle(), article.getRegion()))
                    .toList());
        } catch (RuntimeException exception) {
            log.warn("[article ai] failed 502 : provider call failed, retry next tick : provider={}, size={}, cause={}",
                    articleTitleAnalyzer.getProviderCode(), articles.size(), exception.getClass().getSimpleName());
            return OptionalInt.empty();
        }
        // 규약 위반(결과 수 불일치)은 이 묶음의 건별 실패로 기록
        if (outcomes.size() != articles.size()) {
            log.warn("[article ai] failed : outcome count mismatch : size={}", articles.size());
            articles.forEach(article -> recordFailure(article.getId()));
            return OptionalInt.of(0);
        }

        int doneCount = 0;
        for (int articleIndex = 0; articleIndex < articles.size(); articleIndex++) {
            if (recordOutcome(articles.get(articleIndex).getId(), outcomes.get(articleIndex))) {
                doneCount++;
            }
        }
        return OptionalInt.of(doneCount);
    }

    // 성공 결과 > 성공 기록 (실패 결과이거나 기록 중 예외면 실패 기록)
    private boolean recordOutcome(Long articleId, ArticleTitleAnalysisOutcome outcome) {
        if (!outcome.isSuccessful()) {
            log.warn("[article ai] failed : articleId={}, cause={}", articleId, outcome.failureReason());
            recordFailure(articleId);
            return false;
        }
        try {
            articleAiResultRecorder.recordSuccess(articleId, outcome.analysis());
            return true;
        } catch (RuntimeException exception) {
            log.warn("[article ai] failed : articleId={}, cause={}", articleId, exception.getClass().getSimpleName());
            recordFailure(articleId);
            return false;
        }
    }

    // 실패 기록마저 실패하면 다음 틱에 다시
    private void recordFailure(Long articleId) {
        try {
            articleAiResultRecorder.recordFailure(articleId);
        } catch (RuntimeException recordException) {
            log.error("[article ai] failure record failed : articleId={}", articleId);
        }
    }
}
