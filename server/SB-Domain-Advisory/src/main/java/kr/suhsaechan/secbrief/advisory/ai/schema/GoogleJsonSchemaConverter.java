package kr.suhsaechan.secbrief.advisory.ai.schema;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 제공자 중립 JSON 스키마 > Google(Gemini) Interactions API response_format.schema
 * 규칙 3개 (공식 문서 Structured outputs > JSON Schema support): 문서에 있는 키워드만 남김, nullable 은 type 배열,
 * format 은 date-time·date·time 만
 */
public final class GoogleJsonSchemaConverter {

    // 문서가 지원한다고 적은 키워드 (minLength·maxLength 등은 무시된다고 적혀 있어 미리 뺌)
    private static final Set<String> SUPPORTED_KEYWORDS = Set.of(
            "type", "title", "description", "properties", "required", "additionalProperties", "enum", "format",
            "minimum", "maximum", "items", "prefixItems", "minItems", "maxItems", "anyOf", "$ref", "$defs");
    private static final Set<String> SUPPORTED_FORMATS = Set.of("date-time", "date", "time");

    private GoogleJsonSchemaConverter() {
    }

    public static Map<String, Object> convert(Map<String, Object> outputJsonSchema) {
        return JsonSchemaSubsetConverter.convert(outputJsonSchema, SUPPORTED_KEYWORDS,
                GoogleJsonSchemaConverter::toSupportedSchema);
    }

    // 노드 1개 손질: nullable 표기 > 지원하지 않는 format 제거
    private static Map<String, Object> toSupportedSchema(Map<String, Object> schema) {
        Map<String, Object> supportedSchema = JsonSchemaSubsetConverter.toNullableTypeUnion(schema);
        Object format = supportedSchema.get(JsonSchemaSubsetConverter.FORMAT_KEYWORD);
        if (format == null || SUPPORTED_FORMATS.contains(format)) {
            return supportedSchema;
        }
        Map<String, Object> schemaWithoutFormat = new LinkedHashMap<>(supportedSchema);
        schemaWithoutFormat.remove(JsonSchemaSubsetConverter.FORMAT_KEYWORD);
        return schemaWithoutFormat;
    }
}
