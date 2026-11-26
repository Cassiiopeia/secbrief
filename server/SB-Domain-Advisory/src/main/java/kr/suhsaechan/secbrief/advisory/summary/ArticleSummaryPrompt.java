package kr.suhsaechan.secbrief.advisory.summary;

import static kr.suhsaechan.secbrief.common.json.JsonSupport.OBJECT_MAPPER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;

/**
 * 요약 프롬프트·출력 형식 (제공자 공통): 지시문, 사용자 메시지(기사 1건 JSON), JSON 스키마, 출력 토큰 상한, 응답 JSON > 결과
 * 지시문 글은 작성 규칙 파일(SummaryHarnessGuide)에서 조립: 공통 규칙 + 글 종류별 안내 (+ 고쳐 쓰기 규칙)
 */
public final class ArticleSummaryPrompt {

    public static final String TITLE_FIELD = "title";
    public static final String REGION_FIELD = "region";
    public static final String KIND_FIELD = "kind";
    public static final String SOURCE_TEXT_FIELD = "sourceText";
    public static final String SUMMARY_FIELD = "summary";
    public static final String KEY_POINTS_FIELD = "keyPoints";
    public static final String ACTION_GUIDE_FIELD = "actionGuide";

    // 출력 토큰 상한: 요약 500자 + 핵심 4개 + 안내 3문장 = 한글 900자 안팎 (1자 2토큰으로 넉넉히 잡아 1800) + JSON 틀 여유
    public static final int MAXIMUM_OUTPUT_TOKENS = 2500;

    // 고쳐 쓰기 요청에만 붙는 값: 걸린 초안, 걸린 곳 목록 (필드 이름, 사유, 걸린 글 조각)
    public static final String REJECTED_DRAFT_FIELD = "rejectedDraft";
    public static final String PROBLEMS_FIELD = "problems";
    public static final String PROBLEM_FIELD_NAME_FIELD = "field";
    public static final String PROBLEM_REASON_FIELD = "problem";
    public static final String PROBLEM_FRAGMENT_FIELD = "fragment";

    private static final String GUIDE_SEPARATOR = "\n\n";

    private ArticleSummaryPrompt() {
    }

    /**
     * 작성 지시문: 공통 규칙 + 그 글 종류의 안내 (다른 종류의 안내는 넣지 않음: 지시문이 짧을수록 규칙을 잘 지킴)
     */
    public static String toSystemInstruction(SummaryWritingKind writingKind) {
        return SummaryHarnessGuide.commonRules() + GUIDE_SEPARATOR + SummaryHarnessGuide.writingKindGuide(writingKind);
    }

    /**
     * 고쳐 쓰기 지시문: 작성 지시문 + 고쳐 쓰기 규칙
     */
    public static String toRepairSystemInstruction(SummaryWritingKind writingKind) {
        return toSystemInstruction(writingKind) + GUIDE_SEPARATOR + SummaryHarnessGuide.repairRules();
    }

    /**
     * 사용자 메시지: {title, region, kind, sourceText} JSON 객체
     * 외부 입력을 JSON 문자열로 직렬화해 지시문과 섞이지 않게 함
     */
    public static String toUserMessage(ArticleSummaryRequest request) {
        return toJson(toArticleFields(request));
    }

    /**
     * 고쳐 쓰기 사용자 메시지: 기사 JSON + rejectedDraft(걸린 초안) + problems([{field, problem, fragment}])
     * 초안도 외부 입력에서 나온 글이라 JSON 값으로만 넣음
     */
    public static String toRepairUserMessage(ArticleSummaryRequest request, ArticleSummary rejectedDraft,
                                             List<SummaryProblem> problems) {
        Map<String, Object> draftFields = new LinkedHashMap<>();
        draftFields.put(SUMMARY_FIELD, rejectedDraft.summary());
        draftFields.put(KEY_POINTS_FIELD, rejectedDraft.keyPoints());
        draftFields.put(ACTION_GUIDE_FIELD, rejectedDraft.actionGuide());

        List<Map<String, Object>> problemFieldList = new ArrayList<>();
        for (SummaryProblem problem : problems) {
            Map<String, Object> problemFields = new LinkedHashMap<>();
            problemFields.put(PROBLEM_FIELD_NAME_FIELD, problem.fieldName());
            problemFields.put(PROBLEM_REASON_FIELD, problem.reason());
            problemFields.put(PROBLEM_FRAGMENT_FIELD, problem.fragment());
            problemFieldList.add(problemFields);
        }

        Map<String, Object> repairFields = toArticleFields(request);
        repairFields.put(REJECTED_DRAFT_FIELD, draftFields);
        repairFields.put(PROBLEMS_FIELD, problemFieldList);
        return toJson(repairFields);
    }

    private static Map<String, Object> toArticleFields(ArticleSummaryRequest request) {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put(TITLE_FIELD, request.title());
        article.put(REGION_FIELD, request.region().name());
        article.put(KIND_FIELD, request.kind().name());
        article.put(SOURCE_TEXT_FIELD, request.sourceText());
        return article;
    }

    private static String toJson(Map<String, Object> messageFields) {
        try {
            return OBJECT_MAPPER.writeValueAsString(messageFields);
        } catch (JsonProcessingException exception) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "request is not serializable");
        }
    }

    /**
     * 출력 JSON 스키마: {summary(문자열), keyPoints(문자열 배열), actionGuide(문자열 또는 null)} 모두 필수
     */
    public static Map<String, Object> toOutputJsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(SUMMARY_FIELD, Map.of("type", "string"));
        properties.put(KEY_POINTS_FIELD, Map.of("type", "array", "items", Map.of("type", "string")));
        properties.put(ACTION_GUIDE_FIELD, Map.of("anyOf", List.of(Map.of("type", "string"), Map.of("type", "null"))));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of(SUMMARY_FIELD, KEY_POINTS_FIELD, ACTION_GUIDE_FIELD));
        schema.put("additionalProperties", false);
        return schema;
    }

    /**
     * 응답 JSON > 결과 (검증 전): JSON 객체가 아니거나 summary 문자열이 없으면 실패 결과
     * keyPoints 는 문자열 항목만, actionGuide 는 문자열이 아니면 null
     */
    public static ArticleSummaryOutcome toOutcome(String responseJson) {
        JsonNode responseNode;
        try {
            responseNode = OBJECT_MAPPER.readTree(responseJson == null ? "" : responseJson);
        } catch (JsonProcessingException exception) {
            return ArticleSummaryOutcome.failed("unreadable response");
        }
        if (responseNode == null || !responseNode.isObject() || !responseNode.path(SUMMARY_FIELD).isTextual()) {
            return ArticleSummaryOutcome.failed("unreadable response");
        }
        List<String> keyPoints = new ArrayList<>();
        for (JsonNode keyPointNode : responseNode.path(KEY_POINTS_FIELD)) {
            if (keyPointNode.isTextual()) {
                keyPoints.add(keyPointNode.asText());
            }
        }
        JsonNode actionGuideNode = responseNode.path(ACTION_GUIDE_FIELD);
        return ArticleSummaryOutcome.succeeded(new ArticleSummary(
                responseNode.path(SUMMARY_FIELD).asText(),
                keyPoints,
                actionGuideNode.isTextual() ? actionGuideNode.asText() : null));
    }
}
