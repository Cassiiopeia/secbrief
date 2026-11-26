package kr.suhsaechan.secbrief.application.notification.inbox;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;

/**
 * 알림함 상세: 목록 1행 값 + 본문 전체, 링크(NOTICE), 종류별 기사 묶음(ALERT, 지워진 기사는 뺌), 기사별 대응 안내 요약(ALERT, #71)
 */
public record NotificationDetailResponse(
        Long id,
        NotificationKind kind,
        AlertCategory alertCategory,
        List<AlertCategory> alertCategories,
        String title,
        // NOTICE: 줄바꿈 그대로
        String body,
        String linkUrl,
        Long leadArticleId,
        String leadArticleTitle,
        int articleCount,
        @JsonProperty("isPinned") boolean isPinned,
        Instant createdAt,
        Instant updatedAt,
        Instant pushedAt,
        // 종류 순서 (악용 중 > KISA 긴급 > 고위험), NOTICE 는 빈 목록
        List<NotificationArticleSectionResponse> articleSections,
        // 알림 순서대로 앞 20건까지 (NOTICE 는 빈 목록, 이전 앱 빌드는 읽지 않음)
        List<NotificationArticleGuideResponse> articleGuides
) {
}
