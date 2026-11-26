package kr.suhsaechan.secbrief.tool.summary;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.summary.ArticleBrief;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryPolicy;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryPrompt;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryValidation;

/**
 * 평가 보고서 (마크다운): 생성 방식 1개의 표본 수, 첫 통과율, 최종 통과율, 호출 수, 필드 채움·길이, 걸린 곳, 글 종류별 통과, 채점 평균
 * 숫자만 담음 (표본의 제목·요약 입력·만든 글은 넣지 않음)
 */
public final class SummaryEvaluationReport {

    // 한 줄 결론의 작성 규칙 길이 (지시문에 적은 값: summary-harness/common-rules.md 의 oneLine)
    private static final int ONE_LINE_GUIDE_LENGTH = 60;

    private SummaryEvaluationReport() {
    }

    /**
     * evaluationRows: SummaryEvaluationRunner 출력, scoreRows: SummaryJudge 출력 (없으면 빈 목록)
     */
    public static String toMarkdown(String variantName, List<BackfillTarget> sampleTargets, List<JsonNode> evaluationRows,
                                    List<JsonNode> scoreRows) {
        Map<Long, BackfillTarget> targetById =
                sampleTargets.stream().collect(Collectors.toMap(BackfillTarget::id, target -> target));
        int sampleCount = evaluationRows.size();
        StringBuilder markdown = new StringBuilder("## ").append(variantName).append(" (표본 ").append(sampleCount).append("건)\n\n");
        markdown.append("| 지표 | 값 |\n|---|---:|\n");
        // 첫 초안 검증은 저장된 초안으로 지금 다시 계산 (검증 규칙을 바꾼 뒤에도 같은 초안으로 비교)
        appendRate(markdown, "첫 초안 통과", count(evaluationRows,
                row -> toFirstDraftValidation(targetById, row).isAccepted()), sampleCount);
        appendRate(markdown, "첫 초안이 버린 곳 없이 통과", count(evaluationRows,
                row -> toFirstDraftValidation(targetById, row).isClean()), sampleCount);
        appendRate(markdown, "최종 통과", count(evaluationRows, SummaryEvaluationReport::hasFinalBrief), sampleCount);
        appendRate(markdown, "고쳐 쓴 글 사용", count(evaluationRows,
                row -> row.path("isRepairedBriefUsed").asBoolean()), sampleCount);
        markdown.append(String.format("| 표본당 제공자 호출 | %.2f |%n", evaluationRows.stream()
                .mapToInt(row -> row.path("modelCallCount").asInt()).average().orElse(0)));
        appendFieldRows(markdown, evaluationRows.stream().filter(SummaryEvaluationReport::hasFinalBrief)
                .map(row -> ArticleSummaryPrompt.toOutcome(row.path(SummaryEvaluationRunner.FINAL_BRIEF_FIELD).toString()).brief())
                .toList());

        appendCountTable(markdown, "첫 초안에서 걸린 곳 (필드 + 사유)", evaluationRows.stream()
                .flatMap(row -> toFirstDraftValidation(targetById, row).problems().stream())
                .collect(Collectors.groupingBy(problem -> problem.fieldName() + " " + problem.reason(),
                        TreeMap::new, Collectors.counting())));
        appendCountTable(markdown, "최종 실패 사유", evaluationRows.stream()
                .filter(row -> !hasFinalBrief(row))
                .collect(Collectors.groupingBy(row -> row.path("failureReason").asText("unknown"),
                        TreeMap::new, Collectors.counting())));
        appendWritingKindTable(markdown, targetById, evaluationRows);
        appendScoreTable(markdown, scoreRows);
        return markdown.toString();
    }

    // 최종 브리핑의 필드별 길이·채움 (한눈에 읽히는 길이인지, 빈 필드가 얼마나 되는지)
    private static void appendFieldRows(StringBuilder markdown, List<ArticleBrief> finalBriefs) {
        int briefCount = finalBriefs.size();
        appendAverage(markdown, "한 줄 결론 평균 길이 (자)", finalBriefs, brief -> brief.oneLine().length());
        appendRate(markdown, "한 줄 결론 " + ONE_LINE_GUIDE_LENGTH + "자 이내",
                (int) finalBriefs.stream().filter(brief -> brief.oneLine().length() <= ONE_LINE_GUIDE_LENGTH).count(), briefCount);
        markdown.append(String.format("| 한 줄 결론 최대 길이 (자) | %d |%n",
                finalBriefs.stream().mapToInt(brief -> brief.oneLine().length()).max().orElse(0)));
        appendAverage(markdown, "무슨 일 평균 길이 (자)", finalBriefs, brief -> brief.whatHappened().length());
        appendRate(markdown, "왜 중요 있음",
                (int) finalBriefs.stream().filter(brief -> brief.whyItMatters() != null).count(), briefCount);
        appendRate(markdown, "영향 대상 1개 이상",
                (int) finalBriefs.stream().filter(brief -> !brief.affected().isEmpty()).count(), briefCount);
        appendAverage(markdown, "영향 대상 평균 개수", finalBriefs, brief -> brief.affected().size());
        appendRate(markdown, "대응 1개 이상",
                (int) finalBriefs.stream().filter(brief -> !brief.actions().isEmpty()).count(), briefCount);
        appendAverage(markdown, "대응 평균 개수", finalBriefs, brief -> brief.actions().size());
    }

    // 저장된 첫 초안 > 현재 검증 규칙의 결과 (초안이 없으면 거부)
    private static ArticleSummaryValidation toFirstDraftValidation(Map<Long, BackfillTarget> targetById, JsonNode evaluationRow) {
        JsonNode firstDraft = evaluationRow.path(SummaryEvaluationRunner.FIRST_DRAFT_FIELD);
        if (!firstDraft.isObject()) {
            return ArticleSummaryValidation.rejected("unreadable response", List.of());
        }
        BackfillTarget target = targetById.get(evaluationRow.path("id").asLong());
        return ArticleSummaryPolicy.validate(target.title(), target.sourceText(),
                ArticleSummaryPrompt.toOutcome(firstDraft.toString()).brief());
    }

    private static boolean hasFinalBrief(JsonNode evaluationRow) {
        return evaluationRow.path(SummaryEvaluationRunner.FINAL_BRIEF_FIELD).isObject();
    }

    private static int count(List<JsonNode> rows, Predicate<JsonNode> condition) {
        return (int) rows.stream().filter(condition).count();
    }

    private static void appendRate(StringBuilder markdown, String label, int matchedCount, int totalCount) {
        markdown.append(String.format("| %s | %d / %d (%.0f%%) |%n", label, matchedCount, totalCount,
                totalCount == 0 ? 0 : 100.0 * matchedCount / totalCount));
    }

    private static void appendAverage(StringBuilder markdown, String label, List<ArticleBrief> finalBriefs,
                                      ToIntFunction<ArticleBrief> measure) {
        markdown.append(String.format("| %s | %.2f |%n", label, finalBriefs.stream().mapToInt(measure).average().orElse(0)));
    }

    private static void appendCountTable(StringBuilder markdown, String title, Map<String, Long> countByLabel) {
        markdown.append("\n**").append(title).append("**\n\n");
        if (countByLabel.isEmpty()) {
            markdown.append("없음\n");
            return;
        }
        markdown.append("| 항목 | 건수 |\n|---|---:|\n");
        countByLabel.forEach((label, itemCount) -> markdown.append(String.format("| %s | %d |%n", label, itemCount)));
    }

    private static void appendWritingKindTable(StringBuilder markdown, Map<Long, BackfillTarget> targetById,
                                               List<JsonNode> evaluationRows) {
        markdown.append("\n**글 종류별**\n\n| 글 종류 | 표본 | 첫 초안 통과 | 최종 통과 |\n|---|---:|---:|---:|\n");
        Map<String, List<JsonNode>> rowsByWritingKind = evaluationRows.stream()
                .collect(Collectors.groupingBy(row -> row.path("writingKind").asText(), TreeMap::new, Collectors.toList()));
        rowsByWritingKind.forEach((writingKind, rows) -> markdown.append(String.format("| %s | %d | %d | %d |%n",
                writingKind, rows.size(), count(rows, row -> toFirstDraftValidation(targetById, row).isAccepted()),
                count(rows, SummaryEvaluationReport::hasFinalBrief))));
    }

    private static void appendScoreTable(StringBuilder markdown, List<JsonNode> scoreRows) {
        if (scoreRows.isEmpty()) {
            return;
        }
        markdown.append("\n**채점 평균 (1~5점, 최종 통과 글 ").append(scoreRows.size()).append("건)**\n\n| 기준 | 평균 | 4점 이상 |\n|---|---:|---:|\n");
        for (String criterion : SummaryJudge.CRITERIA) {
            double averageScore = scoreRows.stream().mapToInt(row -> row.path(criterion).asInt()).average().orElse(0);
            int highScoreCount = count(scoreRows, row -> row.path(criterion).asInt() >= 4);
            markdown.append(String.format("| %s | %.2f | %d / %d |%n", criterion, averageScore, highScoreCount, scoreRows.size()));
        }
    }
}
