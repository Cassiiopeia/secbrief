package kr.suhsaechan.secbrief.application.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import kr.suhsaechan.secbrief.member.entity.Notification;
import kr.suhsaechan.secbrief.member.entity.NotificationArticle;
import kr.suhsaechan.secbrief.member.entity.PushDevice;
import kr.suhsaechan.secbrief.member.push.PushSendOutcome;
import kr.suhsaechan.secbrief.member.push.PushSendStatus;
import kr.suhsaechan.secbrief.member.push.PushSender;
import kr.suhsaechan.secbrief.member.repository.PushDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 긴급 알림 유스케이스: 알림 대기 기사 > 알림함 기록 > 기기별로 켠 종류에 맞는 기사만 묶어 1건 > 발송(방해 금지 시간이면 보류) > 기록
 * 기사 제목·푸시 토큰은 로그에 남기지 않음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrgentAlertDispatchService {

    // AI 번역을 기다리는 시간 (넘으면 원문 제목으로 알림, AI 틱 5분보다 길게)
    static final Duration AI_WAIT_DURATION = Duration.ofMinutes(10);
    // 한 틱에 묶는 기사 수 상한 (넘는 분량은 다음 틱)
    static final int MAXIMUM_ARTICLES_PER_TICK = 200;

    private final ArticleRepository articleRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final PushSender pushSender;
    private final UrgentAlertRecorder urgentAlertRecorder;
    private final Clock clock;

    /**
     * 틱 1회: 대기 기사 조회 > 알림함 기록 > 기기별 글 (방해 금지 시간이면 보류) > 발송 > 기록
     * 발송이 전부 실패하면 기록을 취소하고 기사를 대기로 남김 (다음 틱에 다시), 받을 기기가 없거나 알리지 않기로 한 기사도 처리한 것으로 표시
     */
    public UrgentAlertDispatchResult dispatchPendingAlerts() {
        if (!pushSender.isAvailable()) {
            return UrgentAlertDispatchResult.nothingToSend();
        }
        Instant now = clock.instant();
        List<Article> pendingArticles = articleRepository.findUrgentAlertTargets(
                now.minus(AI_WAIT_DURATION), Limit.of(MAXIMUM_ARTICLES_PER_TICK));
        if (pendingArticles.isEmpty()) {
            return UrgentAlertDispatchResult.nothingToSend();
        }

        // 알림함 기록 1건 (알릴 만한 기사 전체, 기기가 없어도 알림함에는 남김)
        List<Article> alertWorthyArticles = UrgentAlertPolicy.toOrderedArticles(
                pendingArticles.stream().filter(UrgentAlertPolicy::isAlertWorthy).toList());
        Notification notification = alertWorthyArticles.isEmpty() ? null : saveAlertNotification(alertWorthyArticles, now);

        // 기기별 글 (켠 종류에 맞는 기사가 없는 기기는 보내지 않음) > 방해 금지 시간이면 보류
        List<PushDevice> pushDevices = notification == null
                ? List.of()
                : pushDeviceRepository.findAllWithEnabledAlertCategories();
        PushDeliveryPlan deliveryPlan = PushDeliveryPlan.of(
                toPushDeliveries(notification, alertWorthyArticles, pushDevices), now);

        List<PushSendOutcome> outcomes = deliveryPlan.pushMessages().isEmpty()
                ? List.of()
                : pushSender.send(deliveryPlan.pushMessages());
        int acceptedCount = PushDeliveryRecorder.countByStatus(outcomes, PushSendStatus.ACCEPTED);
        int failedCount = PushDeliveryRecorder.countByStatus(outcomes, PushSendStatus.FAILED);
        int unregisteredCount = PushDeliveryRecorder.countByStatus(outcomes, PushSendStatus.DEVICE_NOT_REGISTERED);

        // 전부 실패 (제공자 장애 등): 기록 취소, 기사를 대기로 남기고 무효 기기만 정리
        if (acceptedCount == 0 && failedCount > 0) {
            urgentAlertRecorder.cancelDispatch(notification.getId(), outcomes);
            log.warn("[urgent alert] failed 502 : all sends failed : articles={}, devices={}",
                    pendingArticles.size(), deliveryPlan.pushMessages().size());
            return new UrgentAlertDispatchResult(pendingArticles.size(), 0, failedCount, unregisteredCount, true);
        }

        urgentAlertRecorder.recordDispatch(pendingArticles.stream().map(Article::getId).toList(), now,
                notification == null ? null : notification.getId(), deliveryPlan, outcomes);
        log.info("[urgent alert] dispatched : articles={}, accepted={}, failed={}, unregistered={}, held={}",
                pendingArticles.size(), acceptedCount, failedCount, unregisteredCount,
                deliveryPlan.heldDeliveries().size());
        return new UrgentAlertDispatchResult(pendingArticles.size(), acceptedCount, failedCount, unregisteredCount, false);
    }

    // 알림함 기록: 전체 글 + 대표 종류 + 기사 (알림 순서, 기사마다 종류)
    private Notification saveAlertNotification(List<Article> orderedArticles, Instant now) {
        List<NotificationArticle> notificationArticles = new ArrayList<>();
        for (int position = 0; position < orderedArticles.size(); position++) {
            Article article = orderedArticles.get(position);
            notificationArticles.add(new NotificationArticle(position, article.getId(),
                    UrgentAlertPolicy.toAlertCategory(article)));
        }
        return urgentAlertRecorder.saveAlertNotification(UrgentAlertPolicy.toContent(orderedArticles),
                notificationArticles.get(0).getAlertCategory(), notificationArticles, now);
    }

    // 같은 종류 조합을 켠 기기는 같은 글을 받음 (조합마다 글을 한 번만 만듦)
    private static List<PushDelivery> toPushDeliveries(Notification notification, List<Article> alertWorthyArticles,
                                                       List<PushDevice> pushDevices) {
        Map<Set<AlertCategory>, Optional<UrgentAlertContent>> contentByCategories = new HashMap<>();
        List<PushDelivery> pushDeliveries = new ArrayList<>();
        for (PushDevice pushDevice : pushDevices) {
            Optional<UrgentAlertContent> content = contentByCategories.computeIfAbsent(
                    Set.copyOf(pushDevice.getEnabledAlertCategories()),
                    enabledAlertCategories -> toContent(alertWorthyArticles, enabledAlertCategories));
            content.ifPresent(urgentAlertContent -> pushDeliveries.add(new PushDelivery(pushDevice,
                    new PushContent(notification.getId(), NotificationKind.ALERT, urgentAlertContent.title(),
                            urgentAlertContent.body(), urgentAlertContent.articleId(),
                            urgentAlertContent.articleCount()))));
        }
        return pushDeliveries;
    }

    private static Optional<UrgentAlertContent> toContent(List<Article> alertWorthyArticles,
                                                         Set<AlertCategory> enabledAlertCategories) {
        List<Article> matchedArticles = alertWorthyArticles.stream()
                .filter(article -> enabledAlertCategories.contains(UrgentAlertPolicy.toAlertCategory(article)))
                .toList();
        return matchedArticles.isEmpty() ? Optional.empty() : Optional.of(UrgentAlertPolicy.toContent(matchedArticles));
    }
}
