package kr.suhsaechan.secbrief.advisory.summary;

import static kr.suhsaechan.secbrief.common.json.JsonSupport.OBJECT_MAPPER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Objects;

/**
 * 핵심 정리 목록 <-> article.summary_key_points 열 (JSON 문자열 배열)
 * 빈 목록은 null 로 저장, 읽을 수 없는 값은 빈 목록
 */
public final class SummaryKeyPointJson {

    private static final TypeReference<List<String>> KEY_POINT_LIST = new TypeReference<>() {
    };

    private SummaryKeyPointJson() {
    }

    public static String toJson(List<String> keyPoints) {
        if (keyPoints == null || keyPoints.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(keyPoints);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("key points are not serializable", exception);
        }
    }

    public static List<String> toKeyPoints(String keyPointsJson) {
        if (keyPointsJson == null || keyPointsJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> keyPoints = OBJECT_MAPPER.readValue(keyPointsJson, KEY_POINT_LIST);
            return keyPoints == null ? List.of() : keyPoints.stream().filter(Objects::nonNull).toList();
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
