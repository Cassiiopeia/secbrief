package kr.suhsaechan.secbrief.advisory.ai.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysisPrompt;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryPrompt;
import org.junit.jupiter.api.Test;

/**
 * Google(Gemini) 스키마 변환: nullable 표기(type 배열), 문서에 없는 키워드·format 제거, 구조(required·additionalProperties)는 그대로
 * 실제 쓰는 스키마 2개(제목 분석·기사 요약)가 지원 키워드만 남는지도 확인
 */
class GoogleJsonSchemaConverterTest {

    @Test
    void rewritesNullableAnyOfAsTypeUnionAndKeepsOtherAnyOf() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("actionGuide", Map.of("anyOf", List.of(Map.of("type", "string"), Map.of("type", "null"))));
        properties.put("score", Map.of("anyOf", List.of(Map.of("type", "string"), Map.of("type", "integer"))));

        Map<String, Object> converted = GoogleJsonSchemaConverter.convert(Map.of("type", "object", "properties", properties));

        assertThat(findProperty(converted, "actionGuide")).isEqualTo(Map.of("type", List.of("string", "null")));
        assertThat(findProperty(converted, "score"))
                .isEqualTo(Map.of("anyOf", List.of(Map.of("type", "string"), Map.of("type", "integer"))));
    }

    @Test
    void keepsRequiredAsGivenWithoutForcingOptionalPropertiesToBeRequired() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("summary", Map.of("type", "string"));
        properties.put("note", Map.of("type", "string"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("summary"));
        schema.put("additionalProperties", false);

        Map<String, Object> converted = GoogleJsonSchemaConverter.convert(schema);

        assertThat(converted.get("required")).isEqualTo(List.of("summary"));
        assertThat(converted.get("additionalProperties")).isEqualTo(false);
        assertThat(findProperty(converted, "note")).isEqualTo(Map.of("type", "string"));
    }

    @Test
    void dropsUndocumentedKeywordsAndUnsupportedFormats() {
        Map<String, Object> titleSchema = new LinkedHashMap<>();
        titleSchema.put("type", "string");
        titleSchema.put("description", "번역한 제목");
        titleSchema.put("minLength", 1);
        titleSchema.put("pattern", "^.+$");
        titleSchema.put("format", "email");
        Map<String, Object> keywordsSchema = new LinkedHashMap<>();
        keywordsSchema.put("type", "array");
        keywordsSchema.put("items", Map.of("type", "string", "maxLength", 20));
        keywordsSchema.put("maxItems", 5);
        keywordsSchema.put("uniqueItems", true);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("title", titleSchema);
        properties.put("keywords", keywordsSchema);
        properties.put("publishedAt", Map.of("type", "string", "format", "date-time"));

        Map<String, Object> converted = GoogleJsonSchemaConverter.convert(Map.of("type", "object", "properties", properties));

        assertThat(findProperty(converted, "title")).isEqualTo(Map.of("type", "string", "description", "번역한 제목"));
        assertThat(findProperty(converted, "keywords"))
                .isEqualTo(Map.of("type", "array", "items", Map.of("type", "string"), "maxItems", 5));
        assertThat(findProperty(converted, "publishedAt")).isEqualTo(Map.of("type", "string", "format", "date-time"));
    }

    @Test
    void convertsTitleAnalysisAndSummarySchemas() {
        Map<String, Object> titleSchema = GoogleJsonSchemaConverter.convert(ArticleTitleAnalysisPrompt.toOutputJsonSchema());
        Map<String, Object> resultSchema = toMap(findProperty(titleSchema, "results").get("items"));
        assertThat(titleSchema.get("required")).isEqualTo(List.of("results"));
        assertThat(findProperty(resultSchema, "translatedTitle")).isEqualTo(Map.of("type", List.of("string", "null")));
        assertThat(findProperty(resultSchema, "subjectName")).isEqualTo(Map.of("type", List.of("string", "null")));
        Map<String, Object> sourceResultSchema =
                toMap(findProperty(ArticleTitleAnalysisPrompt.toOutputJsonSchema(), "results").get("items"));
        assertThat(toMap(resultSchema.get("properties")).keySet())
                .isEqualTo(toMap(sourceResultSchema.get("properties")).keySet());
        assertThat(resultSchema.get("required")).isEqualTo(sourceResultSchema.get("required"));

        Map<String, Object> summarySchema = GoogleJsonSchemaConverter.convert(ArticleSummaryPrompt.toOutputJsonSchema());
        assertThat(summarySchema.get("required")).isEqualTo(ArticleSummaryPrompt.toOutputJsonSchema().get("required"));
    }

    private static Map<String, Object> findProperty(Map<String, Object> objectSchema, String propertyName) {
        return toMap(toMap(objectSchema.get("properties")).get(propertyName));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object schema) {
        return (Map<String, Object>) schema;
    }
}
