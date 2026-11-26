package kr.suhsaechan.secbrief.tool.summary;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.policy.SourceGroundingPolicy;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryPolicy;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryPrompt;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryValidation;

/**
 * 평가 보고서 (마크다운): 생성 방식 1개의 표본 수, 첫 통과율, 최종 통과율, 호출 수, 거부 사유, 글 종류별 통과, 채점 평균
 * 숫자만 담음 (표본의 제목·요약 입력·만든 글은 넣지 않음)
 */
public final class SummaryEvaluationReport {

    private static final Pattern HANGUL = Pattern.compile("[가-힣]");

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
        appendRate(markdown, "첫 초안 통과 (이전 검증 규칙)", count(evaluationRows,
                row -> isAcceptedByLegacyPolicy(targetById.get(row.path("id").asLong()), row)), sampleCount);
        appendRate(markdown, "첫 초안 통과 (현재 검증 규칙)", count(evaluationRows,
                row -> toFirstDraftValidation(targetById, row).isAccepted()), sampleCount);
        appendRate(markdown, "첫 초안이 버린 곳 없이 통과", count(evaluationRows,
                row -> toFirstDraftValidation(targetById, row).isClean()), sampleCount);
        appendRate(markdown, "최종 통과", count(evaluationRows, SummaryEvaluationReport::hasFinalSummary), sampleCount);
        appendRate(markdown, "고쳐 쓴 글 사용", count(evaluationRows,
                row -> row.path("isRepairedSummaryUsed").asBoolean()), sampleCount);
        markdown.append(String.format("| 표본당 제공자 호출 | %.2f |%n", evaluationRows.stream()
                .mapToInt(row -> row.path("modelCallCount").asInt()).average().orElse(0)));
        List<JsonNode> finalSummaries = evaluationRows.stream().filter(SummaryEvaluationReport::hasFinalSummary)
                .map(row -> row.path(SummaryEvaluationRunner.FINAL_SUMMARY_FIELD)).toList();
        markdown.append(String.format("| 최종 요약 평균 길이 (자) | %.0f |%n", finalSummaries.stream()
                .mapToInt(summary -> summary.path(ArticleSummaryPrompt.SUMMARY_FIELD).asText().length()).average().orElse(0)));
        markdown.append(String.format("| 최종 핵심 정리 평균 개수 | %.2f |%n", finalSummaries.stream()
                .mapToInt(summary -> summary.path(ArticleSummaryPrompt.KEY_POINTS_FIELD).size()).average().orElse(0)));
        appendRate(markdown, "대응 안내 있음 (최종 통과 중)", (int) finalSummaries.stream()
                .filter(summary -> summary.path(ArticleSummaryPrompt.ACTION_GUIDE_FIELD).isTextual()).count(), finalSummaries.size());

        appendCountTable(markdown, "첫 초안에서 걸린 곳 (필드 + 사유)", evaluationRows.stream()
                .flatMap(row -> toList(row.path("firstDraftProblems")).stream())
                .collect(Collectors.groupingBy(problem -> problem.path("field").asText() + " " + problem.path("problem").asText(),
                        TreeMap::new, Collectors.counting())));
        appendCountTable(markdown, "최종 실패 사유", evaluationRows.stream()
                .filter(row -> !hasFinalSummary(row))
                .collect(Collectors.groupingBy(row -> row.path("failureReason").asText("unknown"),
                        TreeMap::new, Collectors.counting())));
        appendWritingKindTable(markdown, targetById, evaluationRows);
        appendScoreTable(markdown, scoreRows);
        return markdown.toString();
    }

    // 이전 검증 규칙 (v0.6.1 운영): 달 이름 번역 허용 이전의 요약 필드 검사 (비교용: 공개 검사 함수를 그대로 조합)
    private static boolean isAcceptedByLegacyPolicy(BackfillTarget target, JsonNode evaluationRow) {
        JsonNode firstDraft = evaluationRow.path(SummaryEvaluationRunner.FIRST_DRAFT_FIELD);
        if (!firstDraft.isObject()) {
            return false;
        }
        String summaryText = firstDraft.path(ArticleSummaryPrompt.SUMMARY_FIELD).asText("").strip();
        return !summaryText.isEmpty()
                && summaryText.length() <= ArticleSummaryPolicy.MAXIMUM_SUMMARY_LENGTH
                && HANGUL.matcher(summaryText).find()
                && !SourceGroundingPolicy.hasNumberNotInSource(target.title() + " " + target.sourceText(), summaryText)
                && !SourceGroundingPolicy.hasVerbatimCopy(target.sourceText(), summaryText,
                        ArticleSummaryPolicy.MINIMUM_VERBATIM_COPY_LENGTH);
    }

    // 저장된 첫 초안 > 현재 검증 규칙의 결과 (초안이 없으면 거부)
    private static ArticleSummaryValidation toFirstDraftValidation(Map<Long, BackfillTarget> targetById, JsonNode evaluationRow) {
        JsonNode firstDraft = evaluationRow.path(SummaryEvaluationRunner.FIRST_DRAFT_FIELD);
        if (!firstDraft.isObject()) {
            return ArticleSummaryValidation.rejected("unreadable response", List.of());
        }
        BackfillTarget target = targetById.get(evaluationRow.path("id").asLong());
        return ArticleSummaryPolicy.validate(target.title(), target.sourceText(),
                ArticleSummaryPrompt.toOutcome(firstDraft.toString()).summary());
    }

    private static boolean hasFinalSummary(JsonNode evaluationRow) {
        return evaluationRow.path(SummaryEvaluationRunner.FINAL_SUMMARY_FIELD).isObject();
    }

    private static int count(List<JsonNode> rows, Predicate<JsonNode> condition) {
        return (int) rows.stream().filter(condition).count();
    }

    private static List<JsonNode> toList(JsonNode arrayNode) {
        List<JsonNode> items = new ArrayList<>();
        arrayNode.forEach(items::add);
        return items;
    }

    private static void appendRate(StringBuilder markdown, String label, int matchedCount, int totalCount) {
        markdown.append(String.format("| %s | %d / %d (%.0f%%) |%n", label, matchedCount, totalCount,
                totalCount == 0 ? 0 : 100.0 * matchedCount / totalCount));
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
                count(rows, SummaryEvaluationReport::hasFinalSummary))));
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
