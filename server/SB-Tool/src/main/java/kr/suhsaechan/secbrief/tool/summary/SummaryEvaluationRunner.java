package kr.suhsaechan.secbrief.tool.summary;

import static kr.suhsaechan.secbrief.common.json.JsonSupport.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummarizer;
import kr.suhsaechan.secbrief.advisory.summary.ArticleBrief;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryAttempt;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryHarness;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryPrompt;
import kr.suhsaechan.secbrief.advisory.summary.SummaryProblem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 평가용 생성: 표본마다 요약 시도 1회 > 첫 초안·검증 결과·최종 브리핑을 한 줄로 기록 (첫 통과율·고쳐 쓰기 효과·채점 입력)
 * 출력 파일에 이미 있는 표본은 건너뜀
 */
@Slf4j
@RequiredArgsConstructor
public class SummaryEvaluationRunner {

    public static final String FINAL_BRIEF_FIELD = "finalBrief";
    // 이전 구조(#49 하네스)로 만든 출력 파일의 최종 글 이름: 구조 비교 채점에서만 읽음
    public static final String LEGACY_FINAL_SUMMARY_FIELD = "finalSummary";
    public static final String FIRST_DRAFT_FIELD = "firstDraft";

    private final ArticleSummarizer articleSummarizer;
    // 표본 1건이 쓸 수 있는 제공자 호출 수 (1: 작성만, 2: 작성 + 고쳐 쓰기)
    private final int maximumModelCallCount;
    private final int concurrency;

    public void run(List<BackfillTarget> sampleTargets, String variantName, Path outputFile) {
        Set<Long> finishedArticleIds = JsonLinesFile.readAll(outputFile).stream()
                .map(row -> row.path("id").asLong()).collect(Collectors.toSet());
        List<BackfillTarget> remainingTargets =
                sampleTargets.stream().filter(target -> !finishedArticleIds.contains(target.id())).toList();
        try (JsonLinesFile.Appender outputAppender = JsonLinesFile.openAppender(outputFile)) {
            ParallelTaskRunner.forEach(remainingTargets, concurrency, target -> {
                ArticleSummaryAttempt attempt =
                        ArticleSummaryHarness.attempt(articleSummarizer, target.toSummaryRequest(), maximumModelCallCount);
                outputAppender.append(toOutputRow(target, variantName, attempt));
                log.info("[summary evaluation] {} : articleId={}, accepted={}, calls={}, cause={}", variantName,
                        target.id(), attempt.isAccepted(), attempt.modelCallCount(), attempt.failureReason());
            });
        }
    }

    private static ObjectNode toOutputRow(BackfillTarget target, String variantName, ArticleSummaryAttempt attempt) {
        ObjectNode row = OBJECT_MAPPER.createObjectNode();
        row.put("id", target.id());
        row.put("variant", variantName);
        row.put("sourceCode", target.sourceCode());
        row.put("writingKind", target.writingKind().name());
        row.put("modelCallCount", attempt.modelCallCount());
        row.set(FIRST_DRAFT_FIELD, toBriefNode(attempt.firstDraft()));
        row.put("isFirstDraftAccepted", attempt.isFirstDraftAccepted());
        row.put("isFirstDraftClean", attempt.isFirstDraftClean());
        ArrayNode problemArray = row.putArray("firstDraftProblems");
        if (attempt.firstDraftValidation() != null) {
            row.put("firstDraftRejectionReason", attempt.firstDraftValidation().rejectionReason());
            for (SummaryProblem problem : attempt.firstDraftValidation().problems()) {
                problemArray.addObject()
                        .put("field", problem.fieldName()).put("problem", problem.reason()).put("fragment", problem.fragment());
            }
        }
        row.set(FINAL_BRIEF_FIELD, toBriefNode(attempt.acceptedBrief()));
        row.put("isRepairedBriefUsed", attempt.isRepairedBriefUsed());
        row.put("failureReason", attempt.failureReason());
        return row;
    }

    static JsonNode toBriefNode(ArticleBrief brief) {
        return brief == null
                ? OBJECT_MAPPER.nullNode()
                : OBJECT_MAPPER.valueToTree(ArticleSummaryPrompt.toBriefFields(brief));
    }
}
