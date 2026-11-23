package kr.suhsaechan.secbrief.advisory.ai;

import static kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysisPrompt.toAnalysis;
import static kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysisPrompt.toOutputJsonSchema;
import static kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysisPrompt.toUserMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * AI 프롬프트: 응답 JSON 변환, 모르는 주제·JSON 아님 거부, 스키마 주제 8종, 사용자 메시지는 제목·지역만
 */
class ArticleTitleAnalysisPromptTest {

    @Test
    void parsesStructuredResponse() {
        assertThat(toAnalysis("{\"topic\":\"supplyChain\",\"translatedTitle\":\"npm 패키지 악성코드\"}"))
                .isEqualTo(new ArticleTitleAnalysis(ArticleTopic.SUPPLY_CHAIN, "npm 패키지 악성코드"));
    }

    @Test
    void parsesNullTranslation() {
        assertThat(toAnalysis("{\"topic\":\"policy\",\"translatedTitle\":null}"))
                .isEqualTo(new ArticleTitleAnalysis(ArticleTopic.POLICY, null));
    }

    @Test
    void rejectsUnknownTopic() {
        assertThatThrownBy(() -> toAnalysis("{\"topic\":\"malware\"}"))
                .extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
    }

    @Test
    void rejectsNonJson() {
        assertThatThrownBy(() -> toAnalysis("sorry")).extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
        assertThatThrownBy(() -> toAnalysis("")).extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
    }

    @Test
    void schemaListsAllEightTopics() {
        assertThat(readTopicEnumValues(toOutputJsonSchema())).containsExactlyInAnyOrder(
                "vulnerability", "ransomware", "privacy", "remoteAccess", "database", "supplyChain", "policy", "general");
    }

    @Test
    void schemaRequiresBothFieldsAndForbidsOthers() {
        Map<String, Object> schema = toOutputJsonSchema();

        assertThat(schema.get("required")).isEqualTo(List.of("topic", "translatedTitle"));
        assertThat(schema.get("additionalProperties")).isEqualTo(false);
    }

    @Test
    void userMessageContainsTitleAndRegionOnly() {
        String userMessage = toUserMessage(new ArticleTitleAnalysisRequest("Critical flaw in Example VPN", ArticleRegion.OVERSEAS));

        assertThat(userMessage).isEqualTo("region: OVERSEAS\ntitle: Critical flaw in Example VPN");
    }

    @SuppressWarnings("unchecked")
    private static List<String> readTopicEnumValues(Map<String, Object> schema) {
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> topic = (Map<String, Object>) properties.get("topic");
        return (List<String>) topic.get("enum");
    }
}
