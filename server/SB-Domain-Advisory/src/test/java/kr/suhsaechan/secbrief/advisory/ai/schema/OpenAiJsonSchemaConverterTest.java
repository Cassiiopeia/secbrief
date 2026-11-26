package kr.suhsaechan.secbrief.advisory.ai.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysisPrompt;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryPrompt;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * OpenAI strict 스키마 변환: nullable 표기, 모든 속성 required, additionalProperties false, 지원하지 않는 키워드 제거, 루트 객체 확인
 * 실제 쓰는 스키마 2개(제목 분석·기사 요약)가 strict 규칙을 지키는지도 확인
 */
class OpenAiJsonSchemaConverterTest {

    @Test
    void rewritesNullableAnyOfAsTypeUnion() {
        Map<String, Object> converted = OpenAiJsonSchemaConverter.convert(toObjectSchema(
                Map.of("actionGuide", Map.of("anyOf", List.of(Map.of("type", "string"), Map.of("type", "null")))),
                List.of("actionGuide")));

        assertThat(findProperty(converted, "actionGuide")).isEqualTo(Map.of("type", List.of("string", "null")));
    }

    @Test
    void addsNullToEnumOfNullableProperty() {
        Map<String, Object> converted = OpenAiJsonSchemaConverter.convert(toObjectSchema(
                Map.of("level", Map.of("anyOf", List.of(
                        Map.of("type", "string", "enum", List.of("high", "low")), Map.of("type", "null")))),
                List.of("level")));

        assertThat(findProperty(converted, "level").get("type")).isEqualTo(List.of("string", "null"));
        assertThat(findProperty(converted, "level").get("enum")).isEqualTo(Arrays.asList("high", "low", null));
    }

    @Test
    void makesEveryPropertyRequiredAndOptionalOnesNullable() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("summary", Map.of("type", "string"));
        properties.put("note", Map.of("type", "string"));
        properties.put("link", Map.of("$ref", "#/$defs/link"));

        Map<String, Object> converted = OpenAiJsonSchemaConverter.convert(toObjectSchema(properties, List.of("summary")));

        assertThat(converted.get("required")).isEqualTo(List.of("summary", "note", "link"));
        assertThat(findProperty(converted, "summary")).isEqualTo(Map.of("type", "string"));
        // 원래 선택이던 속성: null 허용으로 바꿔 required 에 넣음
        assertThat(findProperty(converted, "note")).isEqualTo(Map.of("type", List.of("string", "null")));
        assertThat(findProperty(converted, "link")).isEqualTo(
                Map.of("anyOf", List.of(Map.of("$ref", "#/$defs/link"), Map.of("type", "null"))));
    }

    @Test
    void setsAdditionalPropertiesFalseOnEveryObjectIncludingArrayItems() {
        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");
        itemSchema.put("properties", Map.of("number", Map.of("type", "integer")));
        Map<String, Object> rootSchema = new LinkedHashMap<>();
        rootSchema.put("type", "object");
        rootSchema.put("properties", Map.of("results", Map.of("type", "array", "items", itemSchema)));

        Map<String, Object> converted = OpenAiJsonSchemaConverter.convert(rootSchema);

        assertThat(converted.get("additionalProperties")).isEqualTo(false);
        Map<String, Object> convertedItem = toMap(findProperty(converted, "results").get("items"));
        assertThat(convertedItem.get("additionalProperties")).isEqualTo(false);
        assertThat(convertedItem.get("required")).isEqualTo(List.of("number"));
    }

    @Test
    void dropsKeywordsStrictModeRejectsAndKeepsSupportedOnes() {
        Map<String, Object> titleSchema = new LinkedHashMap<>();
        titleSchema.put("type", "string");
        titleSchema.put("description", "번역한 제목");
        titleSchema.put("minLength", 1);
        titleSchema.put("maxLength", 500);
        titleSchema.put("default", "");
        Map<String, Object> keywordsSchema = new LinkedHashMap<>();
        keywordsSchema.put("type", "array");
        keywordsSchema.put("items", Map.of("type", "string"));
        keywordsSchema.put("maxItems", 5);
        keywordsSchema.put("uniqueItems", true);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("title", titleSchema);
        properties.put("keywords", keywordsSchema);

        Map<String, Object> converted = OpenAiJsonSchemaConverter.convert(toObjectSchema(properties, List.of("title", "keywords")));

        assertThat(findProperty(converted, "title")).isEqualTo(Map.of("type", "string", "description", "번역한 제목"));
        assertThat(findProperty(converted, "keywords"))
                .isEqualTo(Map.of("type", "array", "items", Map.of("type", "string"), "maxItems", 5));
    }

    @Test
    void rejectsSchemaWhoseRootIsNotObject() {
        assertThatThrownBy(() -> OpenAiJsonSchemaConverter.convert(Map.of("type", "array", "items", Map.of("type", "string"))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
        assertThatThrownBy(() -> OpenAiJsonSchemaConverter.convert(
                Map.of("anyOf", List.of(Map.of("type", "object"), Map.of("type", "null")))))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void doesNotChangeSourceSchema() {
        Map<String, Object> sourceSchema = ArticleTitleAnalysisPrompt.toOutputJsonSchema();
        Map<String, Object> sourceCopy = ArticleTitleAnalysisPrompt.toOutputJsonSchema();

        OpenAiJsonSchemaConverter.convert(sourceSchema);

        assertThat(sourceSchema).isEqualTo(sourceCopy);
    }

    @Test
    void convertsTitleAnalysisAndSummarySchemasToStrictForm() {
        Map<String, Object> titleSchema = OpenAiJsonSchemaConverter.convert(ArticleTitleAnalysisPrompt.toOutputJsonSchema());
        Map<String, Object> resultSchema = toMap(findProperty(titleSchema, "results").get("items"));
        assertStrictObject(titleSchema);
        assertStrictObject(resultSchema);
        assertThat(findProperty(resultSchema, "translatedTitle")).isEqualTo(Map.of("type", List.of("string", "null")));
        assertThat(findProperty(resultSchema, "keywords")).isEqualTo(Map.of("type", "array", "items", Map.of("type", "string")));
        assertThat(resultSchema.toString()).doesNotContain("anyOf");

        Map<String, Object> summarySchema = OpenAiJsonSchemaConverter.convert(ArticleSummaryPrompt.toOutputJsonSchema());
        assertStrictObject(summarySchema);
    }

    // strict 객체 규칙: additionalProperties false + 모든 속성이 required
    private static void assertStrictObject(Map<String, Object> objectSchema) {
        assertThat(objectSchema.get("type")).isEqualTo("object");
        assertThat(objectSchema.get("additionalProperties")).isEqualTo(false);
        assertThat(objectSchema.get("required")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .containsExactlyInAnyOrderElementsOf(toMap(objectSchema.get("properties")).keySet());
    }

    private static Map<String, Object> toObjectSchema(Map<String, Object> properties, List<String> requiredNames) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", requiredNames);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> findProperty(Map<String, Object> objectSchema, String propertyName) {
        return toMap(toMap(objectSchema.get("properties")).get(propertyName));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object schema) {
        return (Map<String, Object>) schema;
    }
}
