package kr.suhsaechan.secbrief.application.summary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;
import kr.suhsaechan.secbrief.advisory.policy.SourceTextPolicy;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummarizer;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryAttempt;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryHarness;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryRequest;
import kr.suhsaechan.secbrief.advisory.summary.SummaryWritingKind;
import kr.suhsaechan.secbrief.application.ai.AiDailyBudgetGuard;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 요약 묶음 처리: 제공자 확인 > 하루 예산 확인 > 하루 상한 확인 > 대기(PENDING) 기사 batchSize 건 (중요도 높은 순 > 최신순) > 건별 요약 시도 > 기록
 * 요약 시도 1회 = 작성 > 검증 > 걸리면 고쳐 쓰기 1회 (ArticleSummaryHarness, 제공자 호출 최대 2회), 시도가 실패하면 시도 횟수 +1
 * 한 호출에 1건, 건별 격리 (그 건만 실패), 제공자 호출 실패는 기록 없이 틱 중단, 제목·본문은 로그에 남기지 않음
 */
@Slf4j
@Service
public class ArticleSummaryProcessService {

    private final ArticleSummarizer articleSummarizer;
    private final ArticleRepository articleRepository;
    private final ArticleSummaryResultRecorder articleSummaryResultRecorder;
    private final Clock clock;
    // 한 틱 건수(SUMMARY_BATCH_SIZE)·하루 호출 상한(SUMMARY_DAILY_LIMIT)을 틱마다 읽음 (설정 저장소 > 운영 설정)
    private final SystemConfigService systemConfigService;
    private final AiDailyBudgetGuard aiDailyBudgetGuard;
    // 한 틱이 새 건을 시작할 수 있는 시간: 주기 작업이 스레드 하나를 나눠 써서 느린 응답이 수집·알림을 밀지 않게 함
    private final Duration tickBudget;
    // 호출 수를 센 서울 날짜와 그 날의 호출 수 (서버를 다시 띄우면 0부터, 주기 작업 스레드 하나만 씀)
    private LocalDate countedDate;
    private int countedCallCount;

    public ArticleSummaryProcessService(ArticleSummarizer articleSummarizer, ArticleRepository articleRepository,
                                        ArticleSummaryResultRecorder articleSummaryResultRecorder, Clock clock,
                                        SystemConfigService systemConfigService, AiDailyBudgetGuard aiDailyBudgetGuard,
                                        @Value("${secbrief.summary.tick-budget-seconds}") long tickBudgetSeconds) {
        this.articleSummarizer = articleSummarizer;
        this.articleRepository = articleRepository;
        this.articleSummaryResultRecorder = articleSummaryResultRecorder;
        this.clock = clock;
        this.systemConfigService = systemConfigService;
        this.aiDailyBudgetGuard = aiDailyBudgetGuard;
        this.tickBudget = Duration.ofSeconds(tickBudgetSeconds);
    }

    public SummaryProcessResult processPendingSummaries() {
        if (!articleSummarizer.isAvailable()) {
            log.info("[article summary] skipped : provider not configured, provider={}", articleSummarizer.getProviderCode());
            return new SummaryProcessResult(0, 0, true);
        }
        if (aiDailyBudgetGuard.isBudgetExhausted()) {
            return new SummaryProcessResult(0, 0, true);
        }
        // 하루(서울 날짜) 제공자 호출 상한: 대기열이 한꺼번에 몰려도 AI 비용이 하루 상한을 넘지 않게 함 (남은 건은 다음 날)
        int dailyLimit = systemConfigService.getInt(ConfigKey.SUMMARY_DAILY_LIMIT);
        int remainingDailyCallCount = toRemainingDailyCallCount(dailyLimit);
        if (remainingDailyCallCount <= 0) {
            log.info("[article summary] skipped : daily limit reached, limit={}", dailyLimit);
            return new SummaryProcessResult(0, 0, false);
        }

        int batchSize = systemConfigService.getInt(ConfigKey.SUMMARY_BATCH_SIZE);
        List<Article> pendingArticles =
                articleRepository.findSummaryTargets(Limit.of(Math.min(batchSize, remainingDailyCallCount)));
        Instant tickDeadline = clock.instant().plus(tickBudget);
        int doneCount = 0;
        int failedCount = 0;
        for (Article article : pendingArticles) {
            // 시간 상한·하루 호출 상한을 넘기면 남은 건은 다음 틱에 (고쳐 쓰기 때문에 건수보다 호출 수가 많을 수 있음)
            int remainingModelCallCount = dailyLimit - countedCallCount;
            if (clock.instant().isAfter(tickDeadline) || remainingModelCallCount <= 0) {
                break;
            }
            SummaryStep step = summarizeAndRecord(article, remainingModelCallCount);
            if (step == SummaryStep.PROVIDER_FAILED) {
                break;
            }
            if (step == SummaryStep.DONE) {
                doneCount++;
            } else if (step == SummaryStep.FAILED) {
                failedCount++;
            }
        }
        if (!pendingArticles.isEmpty()) {
            log.info("[article summary] processed : provider={}, done={}, failed={}, read={}",
                    articleSummarizer.getProviderCode(), doneCount, failedCount, pendingArticles.size());
        }
        return new SummaryProcessResult(doneCount, failedCount, false);
    }

    /**
     * 기사 1건: 입력 확인 > 요약 시도(작성 > 검증 > 고쳐 쓰기) > 기록
     * 제공자 호출 실패는 기록 없이 틱 중단, 그 밖의 실패는 시도 횟수 +1
     */
    private SummaryStep summarizeAndRecord(Article article, int remainingModelCallCount) {
        String sourceText = article.getSummaryState().getSourceText();
        // 요약할 내용이 없는 입력 (비어 있음, 주소뿐): 제공자를 부르지 않고 실패로 세어 대기열에서 빠지게 함
        if (!SourceTextPolicy.isWorthSummarizing(sourceText, article.getTitle())) {
            return recordFailure(article.getId(), sourceText, "no source text");
        }
        ArticleSummaryAttempt attempt;
        try {
            attempt = ArticleSummaryHarness.attempt(articleSummarizer, toSummaryRequest(article, sourceText),
                    remainingModelCallCount);
        } catch (RuntimeException exception) {
            log.warn("[article summary] failed 502 : provider call failed, retry next tick : provider={}, cause={}",
                    articleSummarizer.getProviderCode(), exception.getClass().getSimpleName());
            return SummaryStep.PROVIDER_FAILED;
        }
        countedCallCount += attempt.modelCallCount();
        if (!attempt.isAccepted()) {
            return recordFailure(article.getId(), sourceText, attempt.failureReason());
        }
        if (attempt.isRepairedBriefUsed()) {
            log.info("[article summary] repaired : articleId={}, cause={}", article.getId(),
                    attempt.firstDraftValidation().problems().get(0).reason());
        }
        try {
            boolean isRecorded = articleSummaryResultRecorder.recordSuccess(
                    article.getId(), sourceText, attempt.acceptedBrief());
            if (!isRecorded) {
                log.info("[article summary] skipped : source text changed while summarizing : articleId={}", article.getId());
            }
            return isRecorded ? SummaryStep.DONE : SummaryStep.SKIPPED;
        } catch (RuntimeException exception) {
            return recordFailure(article.getId(), sourceText, exception.getClass().getSimpleName());
        }
    }

    // 글 종류: 출처 코드·기사 종류·주제로 판정 (작성 안내를 고르는 기준)
    private static ArticleSummaryRequest toSummaryRequest(Article article, String sourceText) {
        return new ArticleSummaryRequest(article.getTitle(), article.getRegion(), article.getKind(),
                SummaryWritingKind.of(article.getSource().getCode(), article.getKind(), article.getAiAnalysisState().getTopic()), sourceText);
    }

    // 실패 기록마저 실패하면 다음 틱에 다시 (failureReason: 영어 고정 문구 또는 예외 종류)
    private SummaryStep recordFailure(Long articleId, String summarizedSourceText, String failureReason) {
        log.warn("[article summary] failed : articleId={}, cause={}", articleId, failureReason);
        try {
            articleSummaryResultRecorder.recordFailure(articleId, summarizedSourceText);
        } catch (RuntimeException recordException) {
            log.error("[article summary] failure record failed : articleId={}", articleId);
        }
        return SummaryStep.FAILED;
    }

    // 오늘(서울 날짜) 남은 호출 수: 날짜가 바뀌면 0부터 다시 셈
    private int toRemainingDailyCallCount(int dailyLimit) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), SeoulCalendar.SEOUL);
        if (!today.equals(countedDate)) {
            countedDate = today;
            countedCallCount = 0;
        }
        return dailyLimit - countedCallCount;
    }

    private enum SummaryStep {
        DONE,
        // 그 건만 실패: 입력 없음, 거부·읽을 수 없는 응답, 검증 탈락(고쳐 쓴 글도 탈락), 기록 실패
        FAILED,
        // 요약하는 사이 입력이 바뀜: 기록하지 않음
        SKIPPED,
        // 제공자 장애 (기사 탓 아님): 기록 없이 틱 중단 (FAILED 로 굳지 않게 다음 틱에 다시)
        PROVIDER_FAILED
    }
}
