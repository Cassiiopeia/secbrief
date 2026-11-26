package kr.suhsaechan.secbrief.advisory.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 요약 시도 1회: 흠 없는 첫 초안은 호출 1회, 걸리면 걸린 곳을 넘겨 고쳐 쓰기 1회 (호출 최대 2회), 더 나은 쪽을 고름
 * 남은 호출이 1이면 고쳐 쓰지 않음, 고쳐 쓰기 호출 실패는 통과한 첫 초안으로 끝냄
 */
class ArticleSummaryHarnessTest {

    private static final String SOURCE_TEXT = "Attackers are exploiting two flaws in Example VPN 2.4.0 to steal session tokens. "
            + "Example Corp released version 2.4.1.";
    private static final ArticleSummaryRequest REQUEST = new ArticleSummaryRequest("Example VPN flaws exploited",
            ArticleRegion.OVERSEAS, ArticleKind.NEWS, SummaryWritingKind.NEWS_ARTICLE, SOURCE_TEXT);
    private static final ArticleSummary CLEAN_SUMMARY = new ArticleSummary("공격자가 Example VPN 2.4.0 의 결함 두 가지를 악용하고 있어요.",
            List.of("세션 토큰을 노려요."), "2.4.1 버전으로 올리세요.");
    // 입력에 없는 버전(2.4.2)을 쓴 초안
    private static final ArticleSummary REJECTED_SUMMARY = new ArticleSummary("공격자가 Example VPN 2.4.2 의 결함을 악용하고 있어요.",
            List.of("세션 토큰을 노려요."), null);
    private static final ArticleSummary SUMMARY_WITH_BAD_ACTION_GUIDE = new ArticleSummary(
            "공격자가 Example VPN 2.4.0 의 결함 두 가지를 악용하고 있어요.", List.of("세션 토큰을 노려요."), "2.5.0 버전으로 올리세요.");

    @Test
    void cleanFirstDraftUsesOneCallAndNoRepair() {
        ScriptedSummarizer summarizer = new ScriptedSummarizer(ArticleSummaryOutcome.succeeded(CLEAN_SUMMARY), null);

        ArticleSummaryAttempt attempt = attempt(summarizer);

        assertThat(attempt.acceptedSummary()).isEqualTo(CLEAN_SUMMARY);
        assertThat(attempt.modelCallCount()).isEqualTo(1);
        assertThat(attempt.isFirstDraftClean()).isTrue();
        assertThat(attempt.isRepairedSummaryUsed()).isFalse();
        assertThat(summarizer.repairRequests).isEmpty();
    }

    @Test
    void rejectedFirstDraftIsRepairedOnceWithItsProblems() {
        ScriptedSummarizer summarizer = new ScriptedSummarizer(
                ArticleSummaryOutcome.succeeded(REJECTED_SUMMARY), ArticleSummaryOutcome.succeeded(CLEAN_SUMMARY));

        ArticleSummaryAttempt attempt = attempt(summarizer);

        assertThat(attempt.acceptedSummary()).isEqualTo(CLEAN_SUMMARY);
        assertThat(attempt.failureReason()).isNull();
        assertThat(attempt.modelCallCount()).isEqualTo(ArticleSummaryHarness.MAXIMUM_MODEL_CALLS_PER_ATTEMPT);
        assertThat(attempt.isFirstDraftAccepted()).isFalse();
        assertThat(attempt.isRepairedSummaryUsed()).isTrue();
        // 고쳐 쓰기 요청: 걸린 초안 + 걸린 곳(필드, 사유, 입력에 없는 숫자)
        assertThat(summarizer.repairRequests).containsExactly(new RepairRequest(REJECTED_SUMMARY,
                List.of(new SummaryProblem("summary", ArticleSummaryPolicy.REASON_NUMBER_NOT_IN_SOURCE, "2.4.2"))));
    }

    @Test
    void neverCallsMoreThanTwiceEvenWhenRepairIsRejectedToo() {
        ScriptedSummarizer summarizer = new ScriptedSummarizer(ArticleSummaryOutcome.succeeded(REJECTED_SUMMARY),
                ArticleSummaryOutcome.succeeded(new ArticleSummary("Attackers exploit Example VPN.", List.of(), null)));

        ArticleSummaryAttempt attempt = attempt(summarizer);

        assertThat(attempt.isAccepted()).isFalse();
        assertThat(attempt.failureReason()).isEqualTo("summary has number not in source > repair: summary has no korean");
        assertThat(attempt.modelCallCount()).isEqualTo(2);
        assertThat(summarizer.summarizeCallCount + summarizer.repairRequests.size()).isEqualTo(2);
    }

    @Test
    void acceptedDraftWithDroppedPartIsRepairedAndKeptWhenRepairIsNotBetter() {
        // 첫 초안: 통과했지만 대응 안내를 버림 > 고쳐 쓴 글이 거부되면 첫 초안(대응 안내 없이)을 씀
        ScriptedSummarizer summarizer = new ScriptedSummarizer(
                ArticleSummaryOutcome.succeeded(SUMMARY_WITH_BAD_ACTION_GUIDE), ArticleSummaryOutcome.succeeded(REJECTED_SUMMARY));

        ArticleSummaryAttempt attempt = attempt(summarizer);

        assertThat(attempt.acceptedSummary()).isEqualTo(new ArticleSummary(
                SUMMARY_WITH_BAD_ACTION_GUIDE.summary(), SUMMARY_WITH_BAD_ACTION_GUIDE.keyPoints(), null));
        assertThat(attempt.failureReason()).isNull();
        assertThat(attempt.isFirstDraftAccepted()).isTrue();
        assertThat(attempt.isFirstDraftClean()).isFalse();
        assertThat(attempt.isRepairedSummaryUsed()).isFalse();
        assertThat(attempt.modelCallCount()).isEqualTo(2);
    }

    @Test
    void acceptedDraftWithDroppedPartIsReplacedByCleanRepair() {
        ScriptedSummarizer summarizer = new ScriptedSummarizer(
                ArticleSummaryOutcome.succeeded(SUMMARY_WITH_BAD_ACTION_GUIDE), ArticleSummaryOutcome.succeeded(CLEAN_SUMMARY));

        ArticleSummaryAttempt attempt = attempt(summarizer);

        assertThat(attempt.acceptedSummary()).isEqualTo(CLEAN_SUMMARY);
        assertThat(attempt.isRepairedSummaryUsed()).isTrue();
    }

    @Test
    void doesNotRepairWhenOnlyOneModelCallRemains() {
        ScriptedSummarizer summarizer = new ScriptedSummarizer(
                ArticleSummaryOutcome.succeeded(REJECTED_SUMMARY), ArticleSummaryOutcome.succeeded(CLEAN_SUMMARY));

        ArticleSummaryAttempt attempt = ArticleSummaryHarness.attempt(summarizer, REQUEST, 1);

        assertThat(attempt.isAccepted()).isFalse();
        assertThat(attempt.failureReason()).isEqualTo("summary has number not in source");
        assertThat(attempt.modelCallCount()).isEqualTo(1);
        assertThat(summarizer.repairRequests).isEmpty();
    }

    @Test
    void unreadableFirstResponseFailsWithoutRepair() {
        ScriptedSummarizer summarizer = new ScriptedSummarizer(ArticleSummaryOutcome.failed("refused"), null);

        ArticleSummaryAttempt attempt = attempt(summarizer);

        assertThat(attempt.failureReason()).isEqualTo("refused");
        assertThat(attempt.modelCallCount()).isEqualTo(1);
        assertThat(attempt.firstDraft()).isNull();
        assertThat(summarizer.repairRequests).isEmpty();
    }

    @Test
    void unreadableRepairResponseFailsWithBothReasons() {
        ScriptedSummarizer summarizer = new ScriptedSummarizer(
                ArticleSummaryOutcome.succeeded(REJECTED_SUMMARY), ArticleSummaryOutcome.failed("truncated"));

        assertThat(attempt(summarizer).failureReason()).isEqualTo("summary has number not in source > repair: truncated");
    }

    @Test
    void repairCallFailureKeepsAcceptedFirstDraftButPropagatesWhenFirstDraftWasRejected() {
        ScriptedSummarizer acceptedFirst = new ScriptedSummarizer(ArticleSummaryOutcome.succeeded(SUMMARY_WITH_BAD_ACTION_GUIDE), null);
        ScriptedSummarizer rejectedFirst = new ScriptedSummarizer(ArticleSummaryOutcome.succeeded(REJECTED_SUMMARY), null);

        ArticleSummaryAttempt attempt = attempt(acceptedFirst);

        assertThat(attempt.isAccepted()).isTrue();
        assertThat(attempt.modelCallCount()).isEqualTo(1);
        assertThatThrownBy(() -> attempt(rejectedFirst)).isInstanceOf(CustomException.class);
    }

    private static ArticleSummaryAttempt attempt(ArticleSummarizer summarizer) {
        return ArticleSummaryHarness.attempt(summarizer, REQUEST, ArticleSummaryHarness.MAXIMUM_MODEL_CALLS_PER_ATTEMPT);
    }

    private record RepairRequest(ArticleSummary rejectedDraft, List<SummaryProblem> problems) {
    }

    /** 테스트용 요약기: 작성 응답 1개, 고쳐 쓰기 응답 1개 (고쳐 쓰기 응답이 null 이면 호출 실패) */
    private static final class ScriptedSummarizer implements ArticleSummarizer {

        private final ArticleSummaryOutcome summarizeOutcome;
        private final ArticleSummaryOutcome repairOutcome;
        private final List<RepairRequest> repairRequests = new ArrayList<>();
        private int summarizeCallCount;

        private ScriptedSummarizer(ArticleSummaryOutcome summarizeOutcome, ArticleSummaryOutcome repairOutcome) {
            this.summarizeOutcome = summarizeOutcome;
            this.repairOutcome = repairOutcome;
        }

        @Override
        public String getProviderCode() {
            return "scripted";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public ArticleSummaryOutcome summarize(ArticleSummaryRequest request) {
            summarizeCallCount++;
            return summarizeOutcome;
        }

        @Override
        public ArticleSummaryOutcome repair(ArticleSummaryRequest request, ArticleSummary rejectedDraft,
                                            List<SummaryProblem> problems) {
            repairRequests.add(new RepairRequest(rejectedDraft, problems));
            if (repairOutcome == null) {
                throw new CustomException(ErrorCode.AI_API_ERROR, "scripted repair call failure");
            }
            return repairOutcome;
        }
    }
}
