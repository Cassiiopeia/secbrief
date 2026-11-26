package kr.suhsaechan.secbrief.advisory.summary;

import static kr.suhsaechan.secbrief.common.json.JsonSupport.OBJECT_MAPPER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Objects;

/**
 * 브리핑 목록 2개 <-> article 열 (JSON 글): 영향 대상 [{target, detail}] = summary_affected, 대응 ["…"] = summary_actions
 * 빈 목록은 null 로 저장, 읽을 수 없는 값은 빈 목록
 */
public final class ArticleBriefJson {

    private static final TypeReference<List<AffectedTarget>> AFFECTED_TARGET_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> ACTION_LIST = new TypeReference<>() {
    };

    private ArticleBriefJson() {
    }

    public static String toAffectedJson(List<AffectedTarget> affectedTargets) {
        return toJson(affectedTargets);
    }

    public static List<AffectedTarget> toAffectedTargets(String affectedJson) {
        return toList(affectedJson, AFFECTED_TARGET_LIST).stream()
                .filter(affectedTarget -> affectedTarget.target() != null)
                .toList();
    }

    public static String toActionsJson(List<String> actions) {
        return toJson(actions);
    }

    public static List<String> toActions(String actionsJson) {
        return toList(actionsJson, ACTION_LIST);
    }

    private static String toJson(List<?> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(items);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("brief list is not serializable", exception);
        }
    }

    private static <T> List<T> toList(String json, TypeReference<List<T>> listType) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<T> items = OBJECT_MAPPER.readValue(json, listType);
            return items == null ? List.of() : items.stream().filter(Objects::nonNull).toList();
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
