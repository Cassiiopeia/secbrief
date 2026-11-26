package kr.suhsaechan.secbrief.tool.summary;

import static kr.suhsaechan.secbrief.common.json.JsonSupport.OBJECT_MAPPER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletion;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletionClient;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletionRequest;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallType;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryPrompt;
import lombok.extern.slf4j.Slf4j;

/**
 * 평가용 채점 (LLM 심사): 표본 입력 + 최종 글 > 기준 6개를 1~5점으로 (어느 쪽 글인지는 알려 주지 않음)
 * 기준: faithfulness, coverage, actionGuide, koreanStyle, originality, concision (설명은 summary-evaluation/judge-instruction.md)
 * 생성 모델과 다른, 더 강한 모델로만 돌림. 통과한 최종 글이 없는 표본은 채점하지 않음
 */
@Slf4j
public class SummaryJudge {

    public static final List<String> CRITERIA =
            List.of("faithfulness", "coverage", "actionGuide", "koreanStyle", "originality", "concision");
    public static final String ISSUES_FIELD = "issues";

    private static final String JUDGE_INSTRUCTION_RESOURCE = "summary-evaluation/judge-instruction.md";
    private static final String BRIEFING_FIELD = "briefing";
    private static final int MINIMUM_SCORE = 1;
    private static final int MAXIMUM_SCORE = 5;
    // 점수 6개 + 짧은 지적 3개
    private static final int MAXIMUM_OUTPUT_TOKENS = 1000;

    private final StructuredCompletionClient judgeClient;
    private final int concurrency;
    private final String judgeInstruction;

    public SummaryJudge(StructuredCompletionClient judgeClient, int concurrency) {
        this.judgeClient = judgeClient;
        this.concurrency = concurrency;
        this.judgeInstruction = EvaluationResource.read(JUDGE_INSTRUCTION_RESOURCE);
    }

    /**
     * evaluationRows: SummaryEvaluationRunner 의 출력 줄. 점수 파일에 이미 있는 표본은 건너뜀
     */
    public void judge(List<BackfillTarget> sampleTargets, List<JsonNode> evaluationRows, Path scoresFile) {
        Map<Long, BackfillTarget> targetById =
                sampleTargets.stream().collect(Collectors.toMap(BackfillTarget::id, target -> target));
        Set<Long> judgedArticleIds = JsonLinesFile.readAll(scoresFile).stream()
                .map(row -> row.path("id").asLong()).collect(Collectors.toSet());
        List<JsonNode> rowsToJudge = evaluationRows.stream()
                .filter(row -> row.path(SummaryEvaluationRunner.FINAL_SUMMARY_FIELD).isObject())
                .filter(row -> !judgedArticleIds.contains(row.path("id").asLong()))
                .toList();
        try (JsonLinesFile.Appender scoreAppender = JsonLinesFile.openAppender(scoresFile)) {
            ParallelTaskRunner.forEach(rowsToJudge, concurrency, row -> {
                long articleId = row.path("id").asLong();
                JsonNode scores = requestScores(targetById.get(articleId), row.path(SummaryEvaluationRunner.FINAL_SUMMARY_FIELD));
                ObjectNode scoreRow = OBJECT_MAPPER.createObjectNode();
                scoreRow.put("id", articleId);
                scoreRow.put("variant", row.path("variant").asText());
                scoreRow.setAll((ObjectNode) scores);
                scoreAppender.append(scoreRow);
                log.info("[summary evaluation] judged : articleId={}", articleId);
            });
        }
    }

    private JsonNode requestScores(BackfillTarget target, JsonNode finalSummary) {
        Map<String, Object> judgeInput = new LinkedHashMap<>();
        judgeInput.put(ArticleSummaryPrompt.TITLE_FIELD, target.title());
        judgeInput.put("writingKind", target.writingKind().name());
        judgeInput.put(ArticleSummaryPrompt.SOURCE_TEXT_FIELD, target.sourceText());
        judgeInput.put(BRIEFING_FIELD, finalSummary);
        try {
            StructuredCompletion completion = judgeClient.requestCompletion(new StructuredCompletionRequest(
                    AiCallType.SUMMARY, judgeInstruction, OBJECT_MAPPER.writeValueAsString(judgeInput),
                    toScoreJsonSchema(), MAXIMUM_OUTPUT_TOKENS));
            JsonNode scores = OBJECT_MAPPER.readTree(completion.responseJson());
            if (scores == null || !scores.isObject()) {
                throw new IllegalStateException("judge returned no scores : articleId=" + target.id());
            }
            return scores;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("judge response is not readable : articleId=" + target.id(), exception);
        }
    }

    // {기준 6개: 1~5 정수, issues: 문자열 배열} 모두 필수
    static Map<String, Object> toScoreJsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String criterion : CRITERIA) {
            properties.put(criterion, Map.of("type", "integer", "minimum", MINIMUM_SCORE, "maximum", MAXIMUM_SCORE));
        }
        properties.put(ISSUES_FIELD, Map.of("type", "array", "items", Map.of("type", "string")));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        List<String> requiredFields = new ArrayList<>(CRITERIA);
        requiredFields.add(ISSUES_FIELD);
        schema.put("required", requiredFields);
        schema.put("additionalProperties", false);
        return schema;
    }
}
