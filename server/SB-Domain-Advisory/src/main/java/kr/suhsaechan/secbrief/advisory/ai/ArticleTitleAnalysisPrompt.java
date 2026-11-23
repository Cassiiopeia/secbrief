package kr.suhsaechan.secbrief.advisory.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;

/**
 * AI 프롬프트·출력 형식: 지시문, 사용자 메시지(제목·지역만), JSON 스키마, 응답 JSON > 분석 결과
 */
public final class ArticleTitleAnalysisPrompt {

    public static final String TOPIC_FIELD = "topic";
    public static final String TRANSLATED_TITLE_FIELD = "translatedTitle";

    public static final String SYSTEM_INSTRUCTION = """
            You classify one security news headline for a Korean security briefing app.
            You receive only the headline and its region. Never add facts that are not in the headline.

            Pick exactly one topic:
            - vulnerability: software or hardware flaws, CVEs, patches, security updates
            - ransomware: ransomware attacks, extortion groups, data-leak extortion
            - privacy: personal data leaks, privacy violations, data protection incidents
            - remoteAccess: VPN, remote desktop, SSL VPN, remote management tools and gateways
            - database: database products, database exposure, SQL servers
            - supplyChain: attacks on open-source packages, build systems, update or distribution channels
            - policy: laws, regulation, fines, government guidance, official security policy
            - general: anything that fits none of the above

            translatedTitle:
            - If region is OVERSEAS, write a natural Korean headline with the same meaning.
              Keep CVE IDs, version numbers, product names and company names exactly as written in the original.
              Do not add any number, count, version or detail that is not in the original headline.
            - If region is DOMESTIC, set translatedTitle to null.
            """;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ArticleTitleAnalysisPrompt() {
    }

    /**
     * 사용자 메시지: region + title 두 줄 (본문·링크 없음)
     */
    public static String toUserMessage(ArticleTitleAnalysisRequest request) {
        return "region: " + request.region().name() + "\ntitle: " + request.title();
    }

    /**
     * 출력 JSON 스키마: topic(8종 enum, 필수) + translatedTitle(문자열 또는 null, 필수)
     */
    public static Map<String, Object> toOutputJsonSchema() {
        List<String> topicApiValues = Arrays.stream(ArticleTopic.values()).map(ArticleTopic::getApiValue).toList();

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(TOPIC_FIELD, Map.of("type", "string", "enum", topicApiValues));
        properties.put(TRANSLATED_TITLE_FIELD, Map.of("anyOf", List.of(Map.of("type", "string"), Map.of("type", "null"))));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of(TOPIC_FIELD, TRANSLATED_TITLE_FIELD));
        schema.put("additionalProperties", false);
        return schema;
    }

    /**
     * 응답 JSON > 분석 결과 (JSON 아님·모르는 주제는 AI_API_ERROR, 번역 검증은 저장 단계에서)
     */
    public static ArticleTitleAnalysis toAnalysis(String responseJson) {
        JsonNode root = readJson(responseJson);
        ArticleTopic topic = ArticleTopic.fromApiValue(root.path(TOPIC_FIELD).asText(""))
                .orElseThrow(() -> new CustomException(ErrorCode.AI_API_ERROR, "unknown topic"));
        JsonNode translatedTitle = root.path(TRANSLATED_TITLE_FIELD);
        return new ArticleTitleAnalysis(topic, translatedTitle.isTextual() ? translatedTitle.asText() : null);
    }

    private static JsonNode readJson(String responseJson) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseJson == null ? "" : responseJson);
            if (root == null || !root.isObject()) {
                throw new CustomException(ErrorCode.AI_API_ERROR, "response is not a json object");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "response is not json");
        }
    }
}
