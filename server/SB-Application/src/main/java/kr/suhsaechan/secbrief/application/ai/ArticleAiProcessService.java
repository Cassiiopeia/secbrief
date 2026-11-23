package kr.suhsaechan.secbrief.application.ai;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysis;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysisRequest;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalyzer;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * AI 묶음 처리: 키 확인 > 대기(PENDING) 기사 batchSize 건 > 건별 분석 > 성공·실패 기록
 * 건별 격리 (한 건 실패가 나머지를 막지 않음), 제목은 로그에 남기지 않음
 */
@Slf4j
@Service
public class ArticleAiProcessService {

    private final ArticleTitleAnalyzer articleTitleAnalyzer;
    private final ArticleRepository articleRepository;
    private final ArticleAiResultRecorder articleAiResultRecorder;
    private final int batchSize;

    public ArticleAiProcessService(ArticleTitleAnalyzer articleTitleAnalyzer, ArticleRepository articleRepository,
                                   ArticleAiResultRecorder articleAiResultRecorder,
                                   @Value("${secbrief.ai.batch-size:20}") int batchSize) {
        this.articleTitleAnalyzer = articleTitleAnalyzer;
        this.articleRepository = articleRepository;
        this.articleAiResultRecorder = articleAiResultRecorder;
        this.batchSize = batchSize;
    }

    public AiProcessResult processPendingArticles() {
        if (!articleTitleAnalyzer.isAvailable()) {
            log.info("[article ai] skipped : api key missing");
            return new AiProcessResult(0, 0, true);
        }

        List<Article> pendingArticles = articleRepository.findByAiStatusOrderByIdAsc(AiStatus.PENDING, Limit.of(batchSize));
        int doneCount = 0;
        int failedCount = 0;
        for (Article article : pendingArticles) {
            if (analyzeAndRecord(article)) {
                doneCount++;
            } else {
                failedCount++;
            }
        }
        if (!pendingArticles.isEmpty()) {
            log.info("[article ai] processed : done={}, failed={}", doneCount, failedCount);
        }
        return new AiProcessResult(doneCount, failedCount, false);
    }

    // 분석 > 성공 기록 (예외면 실패 기록, 실패 기록마저 실패하면 다음 틱에 다시)
    private boolean analyzeAndRecord(Article article) {
        try {
            ArticleTitleAnalysis analysis = articleTitleAnalyzer.analyze(
                    new ArticleTitleAnalysisRequest(article.getTitle(), article.getRegion()));
            articleAiResultRecorder.recordSuccess(article.getId(), analysis);
            return true;
        } catch (RuntimeException exception) {
            log.warn("[article ai] failed : articleId={}, cause={}", article.getId(), exception.getClass().getSimpleName());
            try {
                articleAiResultRecorder.recordFailure(article.getId());
            } catch (RuntimeException recordException) {
                log.error("[article ai] failure record failed : articleId={}", article.getId());
            }
            return false;
        }
    }
}
