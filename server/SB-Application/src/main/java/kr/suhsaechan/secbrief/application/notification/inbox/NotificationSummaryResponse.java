package kr.suhsaechan.secbrief.application.notification.inbox;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;

/**
 * 알림함 목록 1행: 종류, 대표 알림 종류·묶인 종류(ALERT), 제목, 본문(공지는 앞부분), 대표 기사 번호·제목, 기사 수, 고정, 시각
 */
public record NotificationSummaryResponse(
        Long id,
        NotificationKind kind,
        // NOTICE 는 null
        AlertCategory alertCategory,
        // 묶인 기사의 종류 (중복 없음, 알림 순서), NOTICE 는 빈 목록
        List<AlertCategory> alertCategories,
        String title,
        String body,
        Long leadArticleId,
        // 대표 기사의 지금 표시 제목 (한국어 제목 > 원문 제목), 기사가 지워졌거나 NOTICE 면 null
        String leadArticleTitle,
        int articleCount,
        @JsonProperty("isPinned") boolean isPinned,
        Instant createdAt,
        Instant updatedAt,
        Instant pushedAt
) {
}
