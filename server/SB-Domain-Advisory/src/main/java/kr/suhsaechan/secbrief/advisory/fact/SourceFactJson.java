package kr.suhsaechan.secbrief.advisory.fact;

import static kr.suhsaechan.secbrief.common.json.JsonSupport.OBJECT_MAPPER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Objects;

/**
 * 사실 값 목록 <-> article.source_facts 열 (JSON 배열 [{label, value}])
 * 빈 목록은 null 로 저장, 읽을 수 없는 값은 빈 목록 (상세 화면은 사실 값 없이 열림)
 */
public final class SourceFactJson {

    private static final TypeReference<List<SourceFact>> SOURCE_FACT_LIST = new TypeReference<>() {
    };

    private SourceFactJson() {
    }

    public static String toJson(List<SourceFact> sourceFacts) {
        if (sourceFacts == null || sourceFacts.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(sourceFacts);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("source facts are not serializable", exception);
        }
    }

    public static List<SourceFact> toSourceFacts(String sourceFactsJson) {
        if (sourceFactsJson == null || sourceFactsJson.isBlank()) {
            return List.of();
        }
        try {
            List<SourceFact> sourceFacts = OBJECT_MAPPER.readValue(sourceFactsJson, SOURCE_FACT_LIST);
            return sourceFacts == null ? List.of() : sourceFacts.stream().filter(Objects::nonNull).toList();
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
