package kr.suhsaechan.secbrief.application.notification.settings;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalog;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalogProvider;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.application.notification.AlertCandidate;
import kr.suhsaechan.secbrief.application.notification.AlertRoute;
import kr.suhsaechan.secbrief.application.notification.AlertRoutingPolicy;
import kr.suhsaechan.secbrief.application.notification.DeviceAlertSettings;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.AlertDeliveryMode;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import kr.suhsaechan.secbrief.member.entity.AlertInterests;
import kr.suhsaechan.secbrief.member.entity.Notification;
import kr.suhsaechan.secbrief.member.entity.NotificationArticle;
import kr.suhsaechan.secbrief.member.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 어제 기준 알림 추정 (관심 기술 화면): 어제(기기 날짜) 수집분을 지금 분류·판정 규칙으로 다시 돌림
 * 바로 알림 횟수는 실제 알림 틱 단위: 어제 공통 알림 기록에 든 기사는 그 기록 1번, 나머지는 수집 시각 5분 단위
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertEstimateService {

    // 알림 틱 간격 (secbrief.push.alert-tick-milliseconds 기본값과 같음)
    static final Duration ALERT_TICK_DURATION = Duration.ofMinutes(5);

    private final ArticleRepository articleRepository;
    private final NotificationRepository notificationRepository;
    private final TagCatalogProvider tagCatalogProvider;
    private final Clock clock;

    public AlertEstimateResponse estimate(AlertEstimateRequest request) {
        ZoneId zoneId = toZoneId(request.timeZone());
        LocalDate yesterday = clock.instant().atZone(zoneId).toLocalDate().minusDays(1);
        Instant start = yesterday.atStartOfDay(zoneId).toInstant();
        Instant end = yesterday.plusDays(1).atStartOfDay(zoneId).toInstant();
        TagCatalog catalog = tagCatalogProvider.getCatalog();
        DeviceAlertSettings settings = toSettings(request, catalog);
        Map<Long, Long> sharedNotificationIdByArticleId = findSharedNotificationIdByArticleId(start, end);

        Set<String> immediateTicks = new HashSet<>();
        int immediateArticleCount = 0;
        int digestArticleCount = 0;
        for (Article article : articleRepository.findAllCollectedBetween(start, end)) {
            // 처음 수집한 오래된 소식은 알리지 않음 (Article.create 와 같은 기준)
            if (article.getPublishedAt().isBefore(article.getCollectedAt().minus(Article.URGENT_ALERT_FRESH_DURATION))) {
                continue;
            }
            AlertRoute route = AlertRoutingPolicy.route(AlertCandidate.from(article, catalog), settings).route();
            if (route == AlertRoute.IMMEDIATE) {
                immediateArticleCount++;
                Long sharedNotificationId = sharedNotificationIdByArticleId.get(article.getId());
                immediateTicks.add(sharedNotificationId != null
                        ? "notification-" + sharedNotificationId
                        : "tick-" + article.getCollectedAt().getEpochSecond() / ALERT_TICK_DURATION.toSeconds());
            } else if (route == AlertRoute.DIGEST) {
                digestArticleCount++;
            }
        }
        long currentPushCount = notificationRepository
                .countByKindAndPushDeviceIdIsNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(NotificationKind.ALERT, start, end);
        return new AlertEstimateResponse(yesterday, immediateTicks.size(), immediateArticleCount, digestArticleCount,
                digestArticleCount > 0 ? 1 : 0, currentPushCount);
    }

    private static ZoneId toZoneId(String timeZone) {
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException exception) {
            throw new CustomException(ErrorCode.INVALID_TIME_ZONE);
        }
    }

    private static DeviceAlertSettings toSettings(AlertEstimateRequest request, TagCatalog catalog) {
        Set<AlertCategory> enabledAlertCategories = EnumSet.noneOf(AlertCategory.class);
        request.enabledAlertCategories().stream().filter(AlertCategory::isDeviceToggle).forEach(enabledAlertCategories::add);
        AlertInterests interests = request.interests() == null
                ? AlertInterests.none()
                : AlertSettingsService.toKnownInterests(request.interests(), catalog).toAlertInterests();
        AlertDeliveryMode deliveryMode = request.digest() == null ? AlertDeliveryMode.DIGEST : request.digest().deliveryMode();
        return new DeviceAlertSettings(enabledAlertCategories, interests, deliveryMode);
    }

    // 어제 공통 알림 기록의 기사 > 기록 번호 (같은 틱에 나간 기사 묶음)
    private Map<Long, Long> findSharedNotificationIdByArticleId(Instant start, Instant end) {
        Map<Long, Long> notificationIdByArticleId = new HashMap<>();
        List<Notification> notifications = notificationRepository.findSharedCreatedBetween(NotificationKind.ALERT, start, end);
        for (Notification notification : notifications) {
            for (NotificationArticle notificationArticle : notification.getArticles()) {
                notificationIdByArticleId.putIfAbsent(notificationArticle.getArticleId(), notification.getId());
            }
        }
        return notificationIdByArticleId;
    }
}
