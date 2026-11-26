package kr.suhsaechan.secbrief.advisory.ai.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;

/**
 * 제공자 중립 JSON 스키마 > OpenAI 구조화 출력(strict) 스키마
 * 규칙 5개 (공식 문서 Structured Outputs > Supported schemas): 루트는 객체(anyOf 불가), 모든 속성 required,
 * 객체마다 additionalProperties false, 선택 속성은 null 허용으로 표현, 지원하지 않는 키워드는 뺌
 */
public final class OpenAiJsonSchemaConverter {

    // strict 모드가 받는 키워드 (그 밖의 minLength·maxLength·allOf·not·if 등은 보내면 400)
    private static final Set<String> SUPPORTED_KEYWORDS = Set.of(
            "type", "properties", "required", "additionalProperties", "items", "enum", "const", "anyOf",
            "description", "$defs", "$ref", "pattern", "format",
            "multipleOf", "maximum", "exclusiveMaximum", "minimum", "exclusiveMinimum", "minItems", "maxItems");

    private OpenAiJsonSchemaConverter() {
    }

    /** 변환 (루트가 객체가 아니면 AI_API_ERROR: 이 제공자로는 보낼 수 없는 스키마) */
    public static Map<String, Object> convert(Map<String, Object> outputJsonSchema) {
        if (!JsonSchemaSubsetConverter.OBJECT_TYPE.equals(outputJsonSchema.get(JsonSchemaSubsetConverter.TYPE_KEYWORD))) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "output schema root must be object");
        }
        return JsonSchemaSubsetConverter.convert(outputJsonSchema, SUPPORTED_KEYWORDS,
                OpenAiJsonSchemaConverter::toStrictSchema);
    }

    // 노드 1개 손질: nullable 표기 > 객체면 strict 규칙
    private static Map<String, Object> toStrictSchema(Map<String, Object> schema) {
        Map<String, Object> strictSchema = JsonSchemaSubsetConverter.toNullableTypeUnion(schema);
        return JsonSchemaSubsetConverter.isObjectSchema(strictSchema) ? toStrictObjectSchema(strictSchema) : strictSchema;
    }

    // 객체: 모든 속성 required (원래 선택이던 속성은 null 허용), additionalProperties false
    private static Map<String, Object> toStrictObjectSchema(Map<String, Object> objectSchema) {
        List<?> originallyRequiredNames =
                objectSchema.get(JsonSchemaSubsetConverter.REQUIRED_KEYWORD) instanceof List<?> requiredNames
                        ? requiredNames
                        : List.of();
        Map<String, Object> strictProperties = new LinkedHashMap<>();
        if (objectSchema.get(JsonSchemaSubsetConverter.PROPERTIES_KEYWORD) instanceof Map<?, ?> properties) {
            properties.forEach((propertyName, propertySchema) -> strictProperties.put(String.valueOf(propertyName),
                    originallyRequiredNames.contains(propertyName) ? propertySchema : toNullable(propertySchema)));
        }

        Map<String, Object> strictSchema = new LinkedHashMap<>(objectSchema);
        strictSchema.put(JsonSchemaSubsetConverter.PROPERTIES_KEYWORD, strictProperties);
        strictSchema.put(JsonSchemaSubsetConverter.REQUIRED_KEYWORD, new ArrayList<>(strictProperties.keySet()));
        strictSchema.put(JsonSchemaSubsetConverter.ADDITIONAL_PROPERTIES_KEYWORD, false);
        return strictSchema;
    }

    @SuppressWarnings("unchecked")
    private static Object toNullable(Object propertySchema) {
        return propertySchema instanceof Map<?, ?>
                ? JsonSchemaSubsetConverter.withNullType((Map<String, Object>) propertySchema)
                : propertySchema;
    }
}
