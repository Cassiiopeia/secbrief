package kr.suhsaechan.secbrief.application.notification.inbox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.application.notification.UrgentAlertPolicy;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import kr.suhsaechan.secbrief.member.entity.Notification;
import kr.suhsaechan.secbrief.member.entity.NotificationArticle;
import kr.suhsaechan.secbrief.member.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림함 조회: 종류별 목록(커서 페이지, 한 건 더 읽어 다음 판정), 종류별 최근 번호, 상세(종류별 기사 묶음)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAXIMUM_PAGE_SIZE = 50;
    // 목록에 싣는 공지 본문 앞부분 길이
    static final int NOTICE_PREVIEW_LENGTH = 120;

    private final NotificationRepository notificationRepository;
    private final ArticleRepository articleRepository;

    /**
     * 목록 1페이지: 커서 해석 > 한 건 더 조회 > 대표 기사 제목 붙이기 > 다음 커서
     * pageSize: 없거나 범위 밖이면 기본·상한으로 맞춤
     */
    public NotificationPageResponse findNotifications(NotificationKind kind, String cursor, Integer pageSize) {
        int fetchSize = toPageSize(pageSize);
        List<Notification> notifications = cursor == null || cursor.isBlank()
                ? notificationRepository.findFirstPage(kind, Limit.of(fetchSize + 1))
                : findPageAfter(kind, NotificationCursor.decode(cursor), fetchSize + 1);

        boolean hasNextPage = notifications.size() > fetchSize;
        List<Notification> pageNotifications = hasNextPage ? notifications.subList(0, fetchSize) : notifications;
        Map<Long, Article> leadArticleById = findArticlesById(pageNotifications.stream()
                .map(NotificationQueryService::toLeadArticleId).filter(Objects::nonNull).toList());
        String nextCursor = null;
        if (hasNextPage) {
            Notification lastNotification = pageNotifications.get(pageNotifications.size() - 1);
            nextCursor = NotificationCursor.encode(lastNotification.isPinned(), lastNotification.getCreatedAt(),
                    lastNotification.getId());
        }
        return new NotificationPageResponse(pageNotifications.stream()
                .map(notification -> toSummaryResponse(notification, leadArticleById))
                .toList(), nextCursor);
    }

    public NotificationLatestResponse findLatestIds() {
        return new NotificationLatestResponse(findLatestId(NotificationKind.ALERT), findLatestId(NotificationKind.NOTICE));
    }

    /**
     * 상세: 없거나 보관 기간이 지나 지운 기록은 RESOURCE_NOT_FOUND
     */
    public NotificationDetailResponse findNotification(long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        Map<Long, Article> articleById = findArticlesById(
                notification.getArticles().stream().map(NotificationArticle::getArticleId).toList());
        NotificationSummaryResponse summary = toSummaryResponse(notification, articleById);
        return new NotificationDetailResponse(summary.id(), summary.kind(), summary.alertCategory(),
                summary.alertCategories(), summary.title(), notification.getBody(), notification.getLinkUrl(),
                summary.leadArticleId(), summary.leadArticleTitle(), summary.articleCount(), summary.isPinned(),
                summary.createdAt(), summary.updatedAt(), summary.pushedAt(),
                toArticleSections(notification.getArticles(), articleById));
    }

    private List<Notification> findPageAfter(NotificationKind kind, NotificationCursor cursor, int limit) {
        return notificationRepository.findPageAfter(kind, cursor.isPinned(), cursor.createdAt(), cursor.id(),
                Limit.of(limit));
    }

    private Long findLatestId(NotificationKind kind) {
        return notificationRepository.findTopByKindOrderByIdDesc(kind).map(Notification::getId).orElse(null);
    }

    private Map<Long, Article> findArticlesById(Collection<Long> articleIds) {
        return articleIds.isEmpty()
                ? Map.of()
                : articleRepository.findAllById(articleIds).stream()
                        .collect(Collectors.toMap(Article::getId, Function.identity()));
    }

    // 종류 순서대로 묶음 (기사가 하나도 남지 않은 종류는 뺌)
    private static List<NotificationArticleSectionResponse> toArticleSections(List<NotificationArticle> notificationArticles,
                                                                              Map<Long, Article> articleById) {
        List<NotificationArticleSectionResponse> articleSections = new ArrayList<>();
        for (AlertCategory alertCategory : UrgentAlertPolicy.CATEGORY_PRIORITY) {
            List<ArticleResponse> articles = notificationArticles.stream()
                    .filter(notificationArticle -> notificationArticle.getAlertCategory() == alertCategory)
                    .map(notificationArticle -> articleById.get(notificationArticle.getArticleId()))
                    .filter(Objects::nonNull)
                    .map(ArticleResponse::from)
                    .toList();
            if (!articles.isEmpty()) {
                articleSections.add(new NotificationArticleSectionResponse(alertCategory, articles));
            }
        }
        return articleSections;
    }

    private static NotificationSummaryResponse toSummaryResponse(Notification notification,
                                                                 Map<Long, Article> articleById) {
        Long leadArticleId = toLeadArticleId(notification);
        Article leadArticle = leadArticleId == null ? null : articleById.get(leadArticleId);
        List<AlertCategory> alertCategories = notification.getArticles().stream()
                .map(NotificationArticle::getAlertCategory)
                .distinct()
                .toList();
        return new NotificationSummaryResponse(notification.getId(), notification.getKind(),
                notification.getAlertCategory(), alertCategories, notification.getTitle(), toListBody(notification),
                leadArticleId, leadArticle == null ? null : UrgentAlertPolicy.toDisplayTitle(leadArticle),
                notification.getArticles().size(), notification.isPinned(), notification.getCreatedAt(),
                notification.getUpdatedAt(), notification.getPushedAt());
    }

    private static Long toLeadArticleId(Notification notification) {
        return notification.getArticles().isEmpty() ? null : notification.getArticles().get(0).getArticleId();
    }

    // 공지는 앞부분만 (목록 미리 보기)
    private static String toListBody(Notification notification) {
        String body = notification.getBody();
        return notification.isNotice() && body.length() > NOTICE_PREVIEW_LENGTH
                ? body.substring(0, NOTICE_PREVIEW_LENGTH)
                : body;
    }

    private static int toPageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAXIMUM_PAGE_SIZE);
    }
}
