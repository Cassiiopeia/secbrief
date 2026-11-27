package kr.suhsaechan.secbrief.tool.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import kr.suhsaechan.secbrief.advisory.entity.ArticleSummaryState;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummarizer;
import kr.suhsaechan.secbrief.advisory.summary.AffectedTarget;
import kr.suhsaechan.secbrief.advisory.summary.ArticleBrief;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryHarness;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryOutcome;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryRequest;
import kr.suhsaechan.secbrief.advisory.summary.SummaryProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 브리핑 만들기: 통과하면 1회 시도로 끝, 계속 걸리면 시도 3회·호출 6회에서 멈춤, 입력 없음·내용 없음은 호출하지 않음
 * 결과 파일에는 id 와 만든 글만 (요약 입력 없음), 이미 끝난 기사는 다시 하지 않음
 */
class SummaryBackfillGeneratorTest {

    private static final ArticleBrief CLEAN_BRIEF = new ArticleBrief("Example VPN 결함으로 세션 토큰이 털리고 있어요.",
            "공격자가 Example VPN의 결함을 악용하고 있어요.", null, List.of(new AffectedTarget("Example VPN", "2.4.0")),
            List.of("2.4.1 버전으로 올리세요."));
    // 입력에 없는 버전(9.9.9)을 필수 필드에 쓴 글: 항상 검증에 걸림
    private static final ArticleBrief REJECTED_BRIEF =
            new ArticleBrief("Example VPN에 문제가 있어요.", "Example VPN 9.9.9 버전 문제예요.", null, List.of(), List.of());

    @Test
    void acceptsCleanSummaryWithOneAttemptAndOneCall() {
        CountingSummarizer summarizer = new CountingSummarizer(CLEAN_BRIEF);

        BackfillResult result = new SummaryBackfillGenerator(summarizer, 1)
                .generateOne(BackfillTestData.newsTarget(1L, BackfillTestData.SOURCE_TEXT));

        assertThat(result.status()).isEqualTo(BackfillResult.Status.ACCEPTED);
        assertThat(result.acceptedBrief()).isEqualTo(CLEAN_BRIEF);
        assertThat(result.attemptCount()).isEqualTo(1);
        assertThat(result.modelCallCount()).isEqualTo(1);
        assertThat(summarizer.callCount.get()).isEqualTo(1);
    }

    @Test
    void stopsAfterThreeAttemptsAndSixCallsWhenEveryDraftIsRejected() {
        CountingSummarizer summarizer = new CountingSummarizer(REJECTED_BRIEF);

        BackfillResult result = new SummaryBackfillGenerator(summarizer, 1)
                .generateOne(BackfillTestData.newsTarget(1L, BackfillTestData.SOURCE_TEXT));

        int maximumCallCount = ArticleSummaryState.MAXIMUM_ATTEMPT_COUNT * ArticleSummaryHarness.MAXIMUM_MODEL_CALLS_PER_ATTEMPT;
        assertThat(result.status()).isEqualTo(BackfillResult.Status.REJECTED);
        assertThat(result.acceptedBrief()).isNull();
        assertThat(result.attemptCount()).isEqualTo(ArticleSummaryState.MAXIMUM_ATTEMPT_COUNT);
        assertThat(result.modelCallCount()).isEqualTo(maximumCallCount).isEqualTo(6);
        assertThat(summarizer.callCount.get()).isEqualTo(maximumCallCount);
        assertThat(result.failureReason())
                .isEqualTo("whatHappened has number not in source > repair: whatHappened has number not in source");
    }

    @Test
    void doesNotCallProviderWithoutInputOrWithAddressOnlyInput() {
        CountingSummarizer summarizer = new CountingSummarizer(CLEAN_BRIEF);
        SummaryBackfillGenerator generator = new SummaryBackfillGenerator(summarizer, 1);

        assertThat(generator.generateOne(BackfillTestData.newsTarget(1L, null)).status())
                .isEqualTo(BackfillResult.Status.NOT_RECOVERABLE);
        assertThat(generator.generateOne(BackfillTestData.newsTarget(2L, "https://tracker.example.test/tracker/EXSA-0000-1")).status())
                .isEqualTo(BackfillResult.Status.INPUT_TOO_THIN);
        assertThat(summarizer.callCount.get()).isZero();
    }

    @Test
    void keepsRecoveryFailureReasonOnNotRecoverableResult() {
        SummaryBackfillGenerator generator = new SummaryBackfillGenerator(new CountingSummarizer(CLEAN_BRIEF), 1);
        BackfillTarget blockedTarget = BackfillTarget.fromJson(
                BackfillTarget.withRecoveryFailureReason(BackfillTestData.newsRow(3L, null), "source not supported"));

        assertThat(generator.generateOne(blockedTarget).failureReason()).isEqualTo("source not supported");
    }

    @Test
    void writesResultsWithoutSourceTextAndSkipsFinishedArticlesOnRerun(@TempDir Path temporaryDirectory) {
        Path resultsFile = temporaryDirectory.resolve("results.jsonl");
        List<BackfillTarget> targets = List.of(BackfillTestData.newsTarget(1L, BackfillTestData.SOURCE_TEXT),
                BackfillTestData.newsTarget(2L, null), BackfillTestData.newsTarget(3L, BackfillTestData.SOURCE_TEXT));
        CountingSummarizer firstRunSummarizer = new CountingSummarizer(CLEAN_BRIEF);
        CountingSummarizer secondRunSummarizer = new CountingSummarizer(CLEAN_BRIEF);

        new SummaryBackfillGenerator(firstRunSummarizer, 2).generate(targets, resultsFile);
        new SummaryBackfillGenerator(secondRunSummarizer, 2).generate(targets, resultsFile);

        List<JsonNode> resultRows = JsonLinesFile.readAll(resultsFile);
        assertThat(resultRows).hasSize(3);
        assertThat(firstRunSummarizer.callCount.get()).isEqualTo(2);
        assertThat(secondRunSummarizer.callCount.get()).isZero();
        // 결과 줄: id + 만든 글 + 집계 값뿐 (제목·요약 입력 없음)
        for (JsonNode resultRow : resultRows) {
            assertThat(resultRow.has("source_text")).isFalse();
            assertThat(resultRow.has("sourceText")).isFalse();
            assertThat(resultRow.has("title")).isFalse();
            assertThat(resultRow.toString()).doesNotContain("Attackers are exploiting");
        }
        assertThat(resultRows.stream().map(BackfillResult::fromJson).filter(result -> result.id() == 1L).findFirst().orElseThrow())
                .isEqualTo(new BackfillResult(1L, "THE_HACKER_NEWS", BackfillResult.Status.ACCEPTED, CLEAN_BRIEF, 1, 1, false, null));
    }

    /** 테스트용 요약기: 작성·고쳐 쓰기 모두 같은 글을 돌려주고 호출 수를 셈 */
    private static final class CountingSummarizer implements ArticleSummarizer {

        private final ArticleBrief brief;
        private final AtomicInteger callCount = new AtomicInteger();

        private CountingSummarizer(ArticleBrief brief) {
            this.brief = brief;
        }

        @Override
        public String getProviderCode() {
            return "counting";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public ArticleSummaryOutcome summarize(ArticleSummaryRequest request) {
            callCount.incrementAndGet();
            return ArticleSummaryOutcome.succeeded(brief);
        }

        @Override
        public ArticleSummaryOutcome repair(ArticleSummaryRequest request, ArticleBrief rejectedDraft,
                                            List<SummaryProblem> problems) {
            callCount.incrementAndGet();
            return ArticleSummaryOutcome.succeeded(brief);
        }
    }
}
