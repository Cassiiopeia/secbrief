package kr.suhsaechan.secbrief.application.summary;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetchMethod;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetchResult;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetchTarget;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetcher;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummarizer;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryAttempt;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryHarness;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryRequest;
import kr.suhsaechan.secbrief.advisory.summary.SummaryWritingKind;
import kr.suhsaechan.secbrief.application.ai.AiDailyBudgetGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 브리핑 없는 지난 기사 다시 요약 (기본 꺼짐): 요약 입력 다시 받기 > 요약 시도 1회 (호출 최대 2회) > 브리핑 반영, 실패한 건은 그대로 둠
 * 회차: 최신 id 부터 끝까지 한 번 훑고 다음 서울 날짜에 처음부터 (서버를 다시 띄우면 처음부터)
 */
@Slf4j
@Service
public class SourceTextRefetchSummaryService {

    // 회차 시작 위치 (가장 최신 id 부터)
    private static final long FIRST_CURSOR_ID = Long.MAX_VALUE;

    private final ArticleRepository articleRepository;
    private final SourceTextRefetcher sourceTextRefetcher;
    private final ArticleSummarizer articleSummarizer;
    private final ArticleSummaryResultRecorder articleSummaryResultRecorder;
    private final AiDailyBudgetGuard aiDailyBudgetGuard;
    private final Clock clock;
    private final int batchSize;
    private final int dailyArticleLimit;
    // 이번 회차에서 다음에 볼 id 의 상한 (이 값 미만), 회차를 끝낸 서울 날짜 (진행 중이면 null)
    private long cursorId = FIRST_CURSOR_ID;
    private LocalDate passFinishedDate;
    // 대상 수를 센 서울 날짜와 그 날 본 기사 수 (주기 작업 스레드 하나만 씀)
    private LocalDate countedDate;
    private int countedArticleCount;

    public SourceTextRefetchSummaryService(ArticleRepository articleRepository, SourceTextRefetcher sourceTextRefetcher,
                                           ArticleSummarizer articleSummarizer,
                                           ArticleSummaryResultRecorder articleSummaryResultRecorder,
                                           AiDailyBudgetGuard aiDailyBudgetGuard, Clock clock,
                                           @Value("${secbrief.summary-refetch.batch-size}") int batchSize,
                                           @Value("${secbrief.summary-refetch.daily-article-limit}") int dailyArticleLimit) {
        this.articleRepository = articleRepository;
        this.sourceTextRefetcher = sourceTextRefetcher;
        this.articleSummarizer = articleSummarizer;
        this.articleSummaryResultRecorder = articleSummaryResultRecorder;
        this.aiDailyBudgetGuard = aiDailyBudgetGuard;
        this.clock = clock;
        this.batchSize = batchSize;
        this.dailyArticleLimit = dailyArticleLimit;
    }

    /**
     * 한 틱: 건너뛸 조건 확인 > 대상 읽기 (없으면 회차 끝) > 건별 처리 (제공자 호출 실패면 그 건부터 다음 틱에)
     */
    public SourceTextRefetchSummaryResult processTick() {
        if (!articleSummarizer.isAvailable() || aiDailyBudgetGuard.isBudgetExhausted()) {
            return SourceTextRefetchSummaryResult.SKIPPED;
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), SeoulCalendar.SEOUL);
        if (isWaitingForNextPass(today)) {
            return SourceTextRefetchSummaryResult.SKIPPED;
        }
        int remainingArticleCount = toRemainingDailyArticleCount(today);
        if (remainingArticleCount <= 0) {
            return SourceTextRefetchSummaryResult.SKIPPED;
        }
        List<Article> targets = articleRepository.findSourceTextRefetchTargets(cursorId,
                SourceTextRefetchMethod.toSupportedSourceCodes(), Limit.of(Math.min(batchSize, remainingArticleCount)));
        if (targets.isEmpty()) {
            passFinishedDate = today;
            log.info("[summary refetch] pass finished : nextPassDate={}", today.plusDays(1));
            return SourceTextRefetchSummaryResult.SKIPPED;
        }
        return processTargets(targets);
    }

    // 건별: 다시 받기 > 요약 > 반영 (cursor 는 끝낸 건만큼 전진)
    private SourceTextRefetchSummaryResult processTargets(List<Article> targets) {
        int doneCount = 0;
        int notRecoveredCount = 0;
        int rejectedCount = 0;
        for (Article article : targets) {
            SourceTextRefetchResult refetchResult = sourceTextRefetcher.refetch(toRefetchTarget(article));
            if (!refetchResult.isRecovered()) {
                notRecoveredCount++;
                finishArticle(article);
                continue;
            }
            ArticleSummaryAttempt attempt;
            try {
                attempt = ArticleSummaryHarness.attempt(articleSummarizer, toSummaryRequest(article, refetchResult.sourceText()),
                        ArticleSummaryHarness.MAXIMUM_MODEL_CALLS_PER_ATTEMPT);
            } catch (RuntimeException exception) {
                log.warn("[summary refetch] failed 502 : provider call failed, retry next tick : provider={}, cause={}",
                        articleSummarizer.getProviderCode(), exception.getClass().getSimpleName());
                break;
            }
            finishArticle(article);
            if (attempt.isAccepted() && isBriefRecorded(article.getId(), attempt)) {
                doneCount++;
            } else {
                rejectedCount++;
            }
        }
        log.info("[summary refetch] processed : provider={}, done={}, rejected={}, notRecovered={}, read={}",
                articleSummarizer.getProviderCode(), doneCount, rejectedCount, notRecoveredCount, targets.size());
        return new SourceTextRefetchSummaryResult(doneCount, rejectedCount, notRecoveredCount, false);
    }

    // 기록 실패·그 사이 브리핑이 생긴 건은 반영 안 함 (기사 그대로)
    private boolean isBriefRecorded(Long articleId, ArticleSummaryAttempt attempt) {
        try {
            return articleSummaryResultRecorder.recordRefetchedBrief(articleId, attempt.acceptedBrief());
        } catch (RuntimeException exception) {
            log.warn("[summary refetch] record failed : articleId={}, cause={}", articleId, exception.getClass().getSimpleName());
            return false;
        }
    }

    // 회차를 끝낸 날이면 다음 날까지 쉼, 날짜가 바뀌었으면 처음부터 새 회차
    private boolean isWaitingForNextPass(LocalDate today) {
        if (passFinishedDate == null) {
            return false;
        }
        if (today.equals(passFinishedDate)) {
            return true;
        }
        passFinishedDate = null;
        cursorId = FIRST_CURSOR_ID;
        return false;
    }

    private int toRemainingDailyArticleCount(LocalDate today) {
        if (!today.equals(countedDate)) {
            countedDate = today;
            countedArticleCount = 0;
        }
        return dailyArticleLimit - countedArticleCount;
    }

    private void finishArticle(Article article) {
        cursorId = article.getId();
        countedArticleCount++;
    }

    private static SourceTextRefetchTarget toRefetchTarget(Article article) {
        return new SourceTextRefetchTarget(article.getSource().getCode(), article.getTitle(), article.getOriginalUrl(),
                article.getCveId());
    }

    // 글 종류: 출처 코드·기사 종류·주제로 판정 (요약 틱과 같은 기준)
    private static ArticleSummaryRequest toSummaryRequest(Article article, String sourceText) {
        return new ArticleSummaryRequest(article.getTitle(), article.getRegion(), article.getKind(),
                SummaryWritingKind.of(article.getSource().getCode(), article.getKind(), article.getAiAnalysisState().getTopic()), sourceText);
    }
}
