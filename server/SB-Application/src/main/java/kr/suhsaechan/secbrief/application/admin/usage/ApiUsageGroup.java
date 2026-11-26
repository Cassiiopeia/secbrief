package kr.suhsaechan.secbrief.application.admin.usage;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 공개 API 이용 묶음 (api_usage_daily.usage_group 값 = 이름): 화면 표시 이름, 화면 요청 여부
 * 개인 식별값 없이 요청 경로만 보고 묶음을 정함
 */
@Getter
@RequiredArgsConstructor
public enum ApiUsageGroup {
    TODAY("오늘", true),
    ARTICLE_LIST("소식 목록", true),
    ARTICLE_DETAIL("소식 상세", true),
    // 화면 안 이미지 내려받기: 화면 요청 수에는 넣지 않음 (목록 1번에 여러 번 불림)
    ARTICLE_IMAGE("소식 이미지", false),
    SEARCH("검색", true),
    KEYWORD("키워드", true),
    TREND("주간 추이", true),
    SOURCE("출처 상태", true),
    PUSH_DEVICE("알림 기기 등록", true),
    NOTIFICATION("알림함", true);

    // 집계 대상 경로 앞부분 (그 밖의 경로는 세지 않음: 상태 확인 등)
    private static final String API_PATH_PREFIX = "/api/v1/";
    private static final String PATH_DELIMITER = "/";
    private static final String ARTICLE_SEGMENT = "articles";
    private static final String ARTICLE_SEARCH_SEGMENT = "search";
    private static final String ARTICLE_IMAGE_SEGMENT = "image";
    // 알림 점 확인 (앱을 열 때마다 불림): 화면 요청이 아니라 세지 않음
    private static final String NOTIFICATION_LATEST_SEGMENT = "latest";

    private final String label;
    private final boolean isScreenView;

    /**
     * 화면 요청 묶음 목록 (이용 추이 그래프의 줄 순서)
     */
    public static List<ApiUsageGroup> screenViewGroups() {
        return Arrays.stream(values()).filter(ApiUsageGroup::isScreenView).toList();
    }

    /**
     * 요청 경로 > 묶음 (예: /api/v1/articles/12/image > ARTICLE_IMAGE, 집계 대상이 아니면 빈 값)
     */
    public static Optional<ApiUsageGroup> fromRequestPath(String requestPath) {
        if (requestPath == null || !requestPath.startsWith(API_PATH_PREFIX)) {
            return Optional.empty();
        }
        String[] segments = requestPath.substring(API_PATH_PREFIX.length()).split(PATH_DELIMITER);
        if (segments.length == 0 || segments[0].isEmpty()) {
            return Optional.empty();
        }
        return switch (segments[0]) {
            case "today" -> onlyWhenSingleSegment(segments, TODAY);
            case ARTICLE_SEGMENT -> fromArticleSegments(segments);
            case "keywords" -> Optional.of(KEYWORD);
            case "trends" -> Optional.of(TREND);
            case "sources" -> onlyWhenSingleSegment(segments, SOURCE);
            case "push-devices" -> onlyWhenSingleSegment(segments, PUSH_DEVICE);
            case "notifications" -> segments.length == 2 && NOTIFICATION_LATEST_SEGMENT.equals(segments[1])
                    ? Optional.empty() : Optional.of(NOTIFICATION);
            default -> Optional.empty();
        };
    }

    // articles > 목록, articles/search > 검색, articles/{번호} > 상세, articles/{번호}/image > 이미지
    private static Optional<ApiUsageGroup> fromArticleSegments(String[] segments) {
        if (segments.length == 1) {
            return Optional.of(ARTICLE_LIST);
        }
        if (segments.length == 2) {
            return Optional.of(ARTICLE_SEARCH_SEGMENT.equals(segments[1]) ? SEARCH : ARTICLE_DETAIL);
        }
        return segments.length == 3 && ARTICLE_IMAGE_SEGMENT.equals(segments[2])
                ? Optional.of(ARTICLE_IMAGE) : Optional.empty();
    }

    private static Optional<ApiUsageGroup> onlyWhenSingleSegment(String[] segments, ApiUsageGroup group) {
        return segments.length == 1 ? Optional.of(group) : Optional.empty();
    }
}
