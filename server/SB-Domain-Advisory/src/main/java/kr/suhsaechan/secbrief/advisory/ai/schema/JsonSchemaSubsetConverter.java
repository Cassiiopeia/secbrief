package kr.suhsaechan.secbrief.advisory.ai.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * 제공자 중립 JSON 스키마 > 제공자가 받는 부분집합 (제공자 변환기 공통): 지원 키워드만 남김 > 하위 스키마 재귀 > 노드별 손질
 * 하위 스키마 자리 3종: 이름별(properties, $defs), 목록(anyOf, prefixItems), 1개(items, additionalProperties 가 객체일 때)
 */
final class JsonSchemaSubsetConverter {

    static final String TYPE_KEYWORD = "type";
    static final String PROPERTIES_KEYWORD = "properties";
    static final String REQUIRED_KEYWORD = "required";
    static final String ADDITIONAL_PROPERTIES_KEYWORD = "additionalProperties";
    static final String ANY_OF_KEYWORD = "anyOf";
    static final String ENUM_KEYWORD = "enum";
    static final String FORMAT_KEYWORD = "format";
    static final String OBJECT_TYPE = "object";
    static final String NULL_TYPE = "null";

    private static final Set<String> NAMED_SCHEMAS_KEYWORDS = Set.of(PROPERTIES_KEYWORD, "$defs");
    private static final Set<String> SCHEMA_LIST_KEYWORDS = Set.of(ANY_OF_KEYWORD, "prefixItems");
    private static final Set<String> SINGLE_SCHEMA_KEYWORDS = Set.of("items", ADDITIONAL_PROPERTIES_KEYWORD);
    // nullable 로 접을 수 있는 anyOf 가지 수: [스키마, {type: null}]
    private static final int NULLABLE_ANY_OF_BRANCH_COUNT = 2;

    private JsonSchemaSubsetConverter() {
    }

    /**
     * 스키마 1개 변환 (아래에서 위로): 지원 키워드만 복사 > 하위 스키마 변환 > adjustNode (원본은 바꾸지 않음)
     */
    static Map<String, Object> convert(Map<String, Object> schema, Set<String> supportedKeywords,
                                       UnaryOperator<Map<String, Object>> adjustNode) {
        Map<String, Object> convertedSchema = new LinkedHashMap<>();
        for (Map.Entry<String, Object> keywordEntry : schema.entrySet()) {
            if (supportedKeywords.contains(keywordEntry.getKey())) {
                convertedSchema.put(keywordEntry.getKey(),
                        convertKeywordValue(keywordEntry.getKey(), keywordEntry.getValue(), supportedKeywords, adjustNode));
            }
        }
        return adjustNode.apply(convertedSchema);
    }

    /**
     * anyOf [스키마, {type: null}] > 스키마 + type [원래 타입, "null"] (두 제공자 문서의 nullable 표기), enum 이 있으면 null 추가
     * 가지가 2개가 아니거나 원래 타입이 문자열 1개가 아니면 그대로
     */
    static Map<String, Object> toNullableTypeUnion(Map<String, Object> schema) {
        if (!(schema.get(ANY_OF_KEYWORD) instanceof List<?> branches) || branches.size() != NULLABLE_ANY_OF_BRANCH_COUNT) {
            return schema;
        }
        Map<String, Object> valueBranch = null;
        boolean hasNullBranch = false;
        for (Object branch : branches) {
            if (isNullOnlySchema(branch)) {
                hasNullBranch = true;
            } else if (branch instanceof Map<?, ?> branchSchema && branchSchema.get(TYPE_KEYWORD) instanceof String) {
                valueBranch = toSchema(branchSchema);
            }
        }
        if (!hasNullBranch || valueBranch == null) {
            return schema;
        }
        Map<String, Object> nullableSchema = new LinkedHashMap<>(schema);
        nullableSchema.remove(ANY_OF_KEYWORD);
        nullableSchema.putAll(valueBranch);
        return withNullType(nullableSchema);
    }

    /** null 도 받게: type 문자열 > [타입, "null"], enum 에 null 추가. type 이 없으면(anyOf·$ref) anyOf 로 감쌈 */
    static Map<String, Object> withNullType(Map<String, Object> schema) {
        Object type = schema.get(TYPE_KEYWORD);
        if (type instanceof List<?> typeList && typeList.contains(NULL_TYPE)) {
            return schema;
        }
        if (!(type instanceof String) && !(type instanceof List<?>)) {
            return Map.of(ANY_OF_KEYWORD, List.of(schema, Map.of(TYPE_KEYWORD, NULL_TYPE)));
        }
        Map<String, Object> nullableSchema = new LinkedHashMap<>(schema);
        List<Object> types = new ArrayList<>(type instanceof List<?> typeList ? typeList : List.of(type));
        types.add(NULL_TYPE);
        nullableSchema.put(TYPE_KEYWORD, types);
        // 불변 목록(List.of)은 contains(null) 이 예외라 스트림으로 확인
        if (schema.get(ENUM_KEYWORD) instanceof List<?> enumValues && enumValues.stream().noneMatch(Objects::isNull)) {
            List<Object> nullableEnumValues = new ArrayList<>(enumValues);
            nullableEnumValues.add(null);
            nullableSchema.put(ENUM_KEYWORD, nullableEnumValues);
        }
        return nullableSchema;
    }

    /** 객체 스키마인지 (type 이 object 이거나 object 를 포함) */
    static boolean isObjectSchema(Map<String, Object> schema) {
        Object type = schema.get(TYPE_KEYWORD);
        return OBJECT_TYPE.equals(type) || (type instanceof List<?> typeList && typeList.contains(OBJECT_TYPE));
    }

    private static Object convertKeywordValue(String keyword, Object keywordValue, Set<String> supportedKeywords,
                                              UnaryOperator<Map<String, Object>> adjustNode) {
        if (NAMED_SCHEMAS_KEYWORDS.contains(keyword) && keywordValue instanceof Map<?, ?> namedSchemas) {
            Map<String, Object> convertedSchemas = new LinkedHashMap<>();
            namedSchemas.forEach((schemaName, namedSchema) -> convertedSchemas.put(String.valueOf(schemaName),
                    convertIfSchema(namedSchema, supportedKeywords, adjustNode)));
            return convertedSchemas;
        }
        if (SCHEMA_LIST_KEYWORDS.contains(keyword) && keywordValue instanceof List<?> schemaList) {
            return schemaList.stream()
                    .map(listedSchema -> convertIfSchema(listedSchema, supportedKeywords, adjustNode))
                    .toList();
        }
        if (SINGLE_SCHEMA_KEYWORDS.contains(keyword)) {
            return convertIfSchema(keywordValue, supportedKeywords, adjustNode);
        }
        return keywordValue;
    }

    // 스키마(객체)만 변환, 그 밖(additionalProperties 의 boolean 등)은 그대로
    private static Object convertIfSchema(Object candidate, Set<String> supportedKeywords,
                                          UnaryOperator<Map<String, Object>> adjustNode) {
        return candidate instanceof Map<?, ?> candidateSchema
                ? convert(toSchema(candidateSchema), supportedKeywords, adjustNode)
                : candidate;
    }

    private static boolean isNullOnlySchema(Object candidate) {
        return candidate instanceof Map<?, ?> candidateSchema && candidateSchema.size() == 1
                && NULL_TYPE.equals(candidateSchema.get(TYPE_KEYWORD));
    }

    private static Map<String, Object> toSchema(Map<?, ?> rawSchema) {
        Map<String, Object> schema = new LinkedHashMap<>();
        rawSchema.forEach((keyword, keywordValue) -> schema.put(String.valueOf(keyword), keywordValue));
        return schema;
    }
}
