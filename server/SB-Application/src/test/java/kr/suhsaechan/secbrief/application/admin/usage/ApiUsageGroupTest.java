package kr.suhsaechan.secbrief.application.admin.usage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 요청 경로 > 이용 묶음: 공개 API 10종, 집계 대상이 아닌 경로(알림 점 확인 포함)는 빈 값
 */
class ApiUsageGroupTest {

    @ParameterizedTest
    @CsvSource({
            "/api/v1/today, TODAY",
            "/api/v1/articles, ARTICLE_LIST",
            "/api/v1/articles/search, SEARCH",
            "/api/v1/articles/123, ARTICLE_DETAIL",
            "/api/v1/articles/123/image, ARTICLE_IMAGE",
            "/api/v1/keywords/trending, KEYWORD",
            "/api/v1/trends/weekly, TREND",
            "/api/v1/sources, SOURCE",
            "/api/v1/push-devices, PUSH_DEVICE",
            "/api/v1/notifications, NOTIFICATION",
            "/api/v1/notifications/42, NOTIFICATION"
    })
    void classifiesPublicApiPaths(String requestPath, ApiUsageGroup expectedGroup) {
        assertThat(ApiUsageGroup.fromRequestPath(requestPath)).contains(expectedGroup);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/health", "/api/v1/", "/api/v1/unknown", "/api/v1/articles/1/image/extra",
            "/api/v1/today/extra", "/api/v1/notifications/latest", "/admin", "/actuator/health", ""})
    void ignoresPathsOutsideUsageGroups(String requestPath) {
        assertThat(ApiUsageGroup.fromRequestPath(requestPath)).isEmpty();
    }

    @Test
    void ignoresNullPath() {
        assertThat(ApiUsageGroup.fromRequestPath(null)).isEmpty();
    }

    @Test
    void screenViewGroupsExcludeImageRequests() {
        assertThat(ApiUsageGroup.screenViewGroups())
                .hasSize(ApiUsageGroup.values().length - 1)
                .doesNotContain(ApiUsageGroup.ARTICLE_IMAGE);
    }
}
