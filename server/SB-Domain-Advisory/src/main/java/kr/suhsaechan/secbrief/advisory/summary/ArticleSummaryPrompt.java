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
 * 요약 프롬프트·출력 형식 (제공자 공통): 지시문, 사용자 메시지(기사 1건 JSON), JSON 스키마, 출력 토큰 상한, 응답 JSON > 브리핑
 * 지시문 글은 작성 규칙 파일(SummaryHarnessGuide)에서 조립: 공통 규칙 + 글 종류별 안내 (+ 고쳐 쓰기 규칙)
 */
public final class ArticleSummaryPrompt {

    public static final String TITLE_FIELD = "title";
    public static final String REGION_FIELD = "region";
    public static final String KIND_FIELD = "kind";
    public static final String SOURCE_TEXT_FIELD = "sourceText";

    // 출력 필드 5개 (이름·순서는 앱 응답 brief 와 같음)
    public static final String ONE_LINE_FIELD = "oneLine";
    public static final String WHAT_HAPPENED_FIELD = "whatHappened";
    public static final String WHY_IT_MATTERS_FIELD = "whyItMatters";
    public static final String AFFECTED_FIELD = "affected";
    public static final String AFFECTED_TARGET_FIELD = "target";
    public static final String AFFECTED_DETAIL_FIELD = "detail";
    public static final String ACTIONS_FIELD = "actions";

    // 출력 토큰 상한: 검증 길이 상한을 모두 채운 브리핑(약 1900자)이 2200토큰 안팎 (실측: 한글 1자 약 1.1토큰, 표본 61건은 평균 380 안팎·최대 798토큰)
    // 생각을 끈 호출 기준 값: 생각을 켜면 생각 토큰(실측 호출당 1100~1600)도 이 상한에 포함되므로 함께 올려야 함
    public static final int MAXIMUM_OUTPUT_TOKENS = 2500;

    // 고쳐 쓰기 요청에만 붙는 값: 걸린 초안, 걸린 곳 목록 (필드 이름, 사유, 걸린 글 조각)
    public static final String REJECTED_DRAFT_FIELD = "rejectedDraft";
    public static final String PROBLEMS_FIELD = "problems";
    public static final String PROBLEM_FIELD_NAME_FIELD = "field";
    public static final String PROBLEM_REASON_FIELD = "problem";
    public static final String PROBLEM_FRAGMENT_FIELD = "fragment";

    private static final String GUIDE_SEPARATOR = "\n\n";
    private static final String UNREADABLE_RESPONSE = "unreadable response";
    private static final Map<String, Object> STRING_SCHEMA = Map.of("type", "string");
    private static final Map<String, Object> NULLABLE_STRING_SCHEMA =
            Map.of("anyOf", List.of(STRING_SCHEMA, Map.of("type", "null")));

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
    public static String toRepairUserMessage(ArticleSummaryRequest request, ArticleBrief rejectedDraft,
                                             List<SummaryProblem> problems) {
        List<Map<String, Object>> problemFieldList = new ArrayList<>();
        for (SummaryProblem problem : problems) {
            Map<String, Object> problemFields = new LinkedHashMap<>();
            problemFields.put(PROBLEM_FIELD_NAME_FIELD, problem.fieldName());
            problemFields.put(PROBLEM_REASON_FIELD, problem.reason());
            problemFields.put(PROBLEM_FRAGMENT_FIELD, problem.fragment());
            problemFieldList.add(problemFields);
        }

        Map<String, Object> repairFields = toArticleFields(request);
        repairFields.put(REJECTED_DRAFT_FIELD, toBriefFields(rejectedDraft));
        repairFields.put(PROBLEMS_FIELD, problemFieldList);
        return toJson(repairFields);
    }

    /**
     * 브리핑 > 출력 스키마와 같은 이름·순서의 값 (고쳐 쓰기 요청의 초안, 로컬 도구의 결과 파일)
     */
    public static Map<String, Object> toBriefFields(ArticleBrief brief) {
        List<Map<String, Object>> affectedFieldList = new ArrayList<>();
        for (AffectedTarget affectedTarget : brief.affected()) {
            Map<String, Object> affectedFields = new LinkedHashMap<>();
            affectedFields.put(AFFECTED_TARGET_FIELD, affectedTarget.target());
            affectedFields.put(AFFECTED_DETAIL_FIELD, affectedTarget.detail());
            affectedFieldList.add(affectedFields);
        }
        Map<String, Object> briefFields = new LinkedHashMap<>();
        briefFields.put(ONE_LINE_FIELD, brief.oneLine());
        briefFields.put(WHAT_HAPPENED_FIELD, brief.whatHappened());
        briefFields.put(WHY_IT_MATTERS_FIELD, brief.whyItMatters());
        briefFields.put(AFFECTED_FIELD, affectedFieldList);
        briefFields.put(ACTIONS_FIELD, brief.actions());
        return briefFields;
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
     * 출력 JSON 스키마: {oneLine, whatHappened, whyItMatters(또는 null), affected[{target, detail(또는 null)}], actions[]} 모두 필수
     * 속성 순서 = 쓰는 순서 (제공자는 스키마 순서대로 채움): 한 줄 결론을 먼저 쓰고 무슨 일이 그 뒤를 이어 감
     * (결론을 마지막에 쓰게 하면 무슨 일의 첫 문장을 줄인 글이 되어 두 필드가 되풀이됨: docs/summary-harness/evaluation.md 의 고친 순서)
     * 길이·개수 제한은 스키마에 넣지 않음 (구조화 출력이 maxLength·maxItems 를 받지 않음): 지시문에 적고 ArticleSummaryPolicy 가 검증
     */
    public static Map<String, Object> toOutputJsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(ONE_LINE_FIELD, STRING_SCHEMA);
        properties.put(WHAT_HAPPENED_FIELD, STRING_SCHEMA);
        properties.put(WHY_IT_MATTERS_FIELD, NULLABLE_STRING_SCHEMA);
        properties.put(AFFECTED_FIELD, Map.of("type", "array", "items", toAffectedItemSchema()));
        properties.put(ACTIONS_FIELD, Map.of("type", "array", "items", STRING_SCHEMA));
        return toObjectSchema(properties);
    }

    private static Map<String, Object> toAffectedItemSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(AFFECTED_TARGET_FIELD, STRING_SCHEMA);
        properties.put(AFFECTED_DETAIL_FIELD, NULLABLE_STRING_SCHEMA);
        return toObjectSchema(properties);
    }

    // 객체 스키마: 적은 속성 모두 필수, 그 밖의 속성 금지
    private static Map<String, Object> toObjectSchema(Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.copyOf(properties.keySet()));
        schema.put("additionalProperties", false);
        return schema;
    }

    /**
     * 응답 JSON > 브리핑 (검증 전): JSON 객체가 아니면 실패 결과
     * 글 필드는 문자열이 아니면 null, affected 는 target 이 문자열인 객체만, actions 는 문자열 항목만 (빈 필수 필드는 검증이 거름)
     */
    public static ArticleSummaryOutcome toOutcome(String responseJson) {
        JsonNode responseNode;
        try {
            responseNode = OBJECT_MAPPER.readTree(responseJson == null ? "" : responseJson);
        } catch (JsonProcessingException exception) {
            return ArticleSummaryOutcome.failed(UNREADABLE_RESPONSE);
        }
        if (responseNode == null || !responseNode.isObject()) {
            return ArticleSummaryOutcome.failed(UNREADABLE_RESPONSE);
        }
        List<AffectedTarget> affectedTargets = new ArrayList<>();
        for (JsonNode affectedNode : responseNode.path(AFFECTED_FIELD)) {
            if (affectedNode.path(AFFECTED_TARGET_FIELD).isTextual()) {
                affectedTargets.add(new AffectedTarget(affectedNode.path(AFFECTED_TARGET_FIELD).asText(),
                        toTextOrNull(affectedNode.path(AFFECTED_DETAIL_FIELD))));
            }
        }
        List<String> actions = new ArrayList<>();
        for (JsonNode actionNode : responseNode.path(ACTIONS_FIELD)) {
            if (actionNode.isTextual()) {
                actions.add(actionNode.asText());
            }
        }
        return ArticleSummaryOutcome.succeeded(new ArticleBrief(
                toTextOrNull(responseNode.path(ONE_LINE_FIELD)),
                toTextOrNull(responseNode.path(WHAT_HAPPENED_FIELD)),
                toTextOrNull(responseNode.path(WHY_IT_MATTERS_FIELD)),
                affectedTargets,
                actions));
    }

    private static String toTextOrNull(JsonNode textNode) {
        return textNode.isTextual() ? textNode.asText() : null;
    }
}
