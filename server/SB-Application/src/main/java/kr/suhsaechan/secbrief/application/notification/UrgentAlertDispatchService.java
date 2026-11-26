package kr.suhsaechan.secbrief.application.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalog;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalogProvider;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import kr.suhsaechan.secbrief.member.entity.Notification;
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
 * 알림 유스케이스: 판정 대기 기사 > 공통 기록(긴급) > 기기별 판정(바로·아침 요약·안 알림) > 발송(방해 금지 시간이면 보류) > 기록
 * AI 판단이 끝난 기사만 후보 (60분 지나면 규칙만으로, #97), 기사 제목·푸시 토큰은 로그에 남기지 않음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrgentAlertDispatchService {

    // 한 틱에 판정하는 기사 수 상한 (넘는 분량은 다음 틱)
    static final int MAXIMUM_ARTICLES_PER_TICK = 200;

    private final ArticleRepository articleRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final PushSender pushSender;
    private final UrgentAlertRecorder urgentAlertRecorder;
    private final TagCatalogProvider tagCatalogProvider;
    private final Clock clock;

    /**
     * 틱 1회: 후보 조회 > 공통 기록 > 기기별 판정·글 > 발송 > 기록
     * 발송이 전부 실패하면 기록을 취소하고 기사를 대기로 남김 (다음 틱에 다시), 받을 기기가 없거나 알리지 않는 기사도 처리한 것으로 표시
     */
    public UrgentAlertDispatchResult dispatchPendingAlerts() {
        if (!pushSender.isAvailable()) {
            return UrgentAlertDispatchResult.nothingToSend();
        }
        Instant now = clock.instant();
        List<Article> pendingArticles = articleRepository.findAlertCandidates(
                now.minus(Article.AI_JUDGEMENT_HOLD_DURATION), Limit.of(MAXIMUM_ARTICLES_PER_TICK));
        if (pendingArticles.isEmpty()) {
            return UrgentAlertDispatchResult.nothingToSend();
        }
        TagCatalog catalog = tagCatalogProvider.getCatalog();
        List<AlertCandidate> candidates = pendingArticles.stream()
                .map(article -> AlertCandidate.from(article, catalog))
                .toList();

        // 공통 기록 1건 (알릴 만한 긴급 기사 전체, 기기가 없어도 알림함에는 남김)
        List<Article> urgentArticles = UrgentAlertPolicy.toOrderedArticles(candidates.stream()
                .filter(candidate -> candidate.urgentCategory() != null)
                .map(AlertCandidate::article)
                .toList());
        Notification sharedNotification = urgentArticles.isEmpty()
                ? null
                : urgentAlertRecorder.saveSharedAlertNotification(urgentArticles, now);

        // 기기별 판정 > 바로 알림 글 + 아침 요약 대기분
        DeviceDispatchPlan dispatchPlan = toDeviceDispatchPlan(candidates, sharedNotification, now);
        PushDeliveryPlan deliveryPlan = PushDeliveryPlan.of(dispatchPlan.pushDeliveries(), now);
        List<Long> notificationIds = new ArrayList<>(dispatchPlan.deviceNotificationIds());
        if (sharedNotification != null) {
            notificationIds.add(sharedNotification.getId());
        }

        List<PushSendOutcome> outcomes = deliveryPlan.pushMessages().isEmpty()
                ? List.of()
                : pushSender.send(deliveryPlan.pushMessages());
        int acceptedCount = PushDeliveryRecorder.countByStatus(outcomes, PushSendStatus.ACCEPTED);
        int failedCount = PushDeliveryRecorder.countByStatus(outcomes, PushSendStatus.FAILED);
        int unregisteredCount = PushDeliveryRecorder.countByStatus(outcomes, PushSendStatus.DEVICE_NOT_REGISTERED);

        // 전부 실패 (제공자 장애 등): 기록 취소, 기사를 대기로 남기고 무효 기기만 정리
        if (acceptedCount == 0 && failedCount > 0) {
            urgentAlertRecorder.cancelDispatch(notificationIds, outcomes);
            log.warn("[urgent alert] failed 502 : all sends failed : articles={}, devices={}",
                    pendingArticles.size(), deliveryPlan.pushMessages().size());
            return new UrgentAlertDispatchResult(pendingArticles.size(), 0, failedCount, unregisteredCount, true);
        }

        urgentAlertRecorder.recordDispatch(pendingArticles.stream().map(Article::getId).toList(), now,
                notificationIds, deliveryPlan, outcomes, dispatchPlan.digestQueueItems());
        log.info("[urgent alert] dispatched : articles={}, accepted={}, failed={}, unregistered={}, held={}, digestQueued={}",
                pendingArticles.size(), acceptedCount, failedCount, unregisteredCount,
                deliveryPlan.heldDeliveries().size(), dispatchPlan.digestQueueItems().size());
        return new UrgentAlertDispatchResult(pendingArticles.size(), acceptedCount, failedCount, unregisteredCount, false);
    }

    // 기기마다: 판정 > 바로 알림 글 (공통 기록에 다 있으면 그 번호, 아니면 기기 전용 기록) + 아침 요약 대기분
    private DeviceDispatchPlan toDeviceDispatchPlan(List<AlertCandidate> candidates, Notification sharedNotification,
                                                    Instant now) {
        Set<Long> sharedArticleIds = sharedNotification == null
                ? Set.of()
                : candidates.stream().filter(candidate -> candidate.urgentCategory() != null)
                        .map(AlertCandidate::articleId).collect(Collectors.toSet());
        List<PushDelivery> pushDeliveries = new ArrayList<>();
        List<Long> deviceNotificationIds = new ArrayList<>();
        List<DigestQueueItem> digestQueueItems = new ArrayList<>();
        for (PushDevice pushDevice : pushDeviceRepository.findAllWithAlertSettings()) {
            DeviceAlertSettings settings = DeviceAlertSettings.from(pushDevice);
            List<AlertedArticle> immediateArticles = new ArrayList<>();
            for (AlertCandidate candidate : candidates) {
                AlertRouteDecision decision = AlertRoutingPolicy.route(candidate, settings);
                if (decision.route() == AlertRoute.IMMEDIATE) {
                    immediateArticles.add(new AlertedArticle(candidate.article(), decision.alertCategory()));
                } else if (decision.route() == AlertRoute.DIGEST) {
                    digestQueueItems.add(new DigestQueueItem(pushDevice.getId(), candidate.articleId()));
                }
            }
            if (immediateArticles.isEmpty()) {
                continue;
            }
            UrgentAlertContent content = UrgentAlertPolicy.toContent(immediateArticles);
            boolean isCoveredBySharedRecord = sharedNotification != null && immediateArticles.stream()
                    .allMatch(alertedArticle -> sharedArticleIds.contains(alertedArticle.article().getId()));
            long notificationId;
            if (isCoveredBySharedRecord) {
                notificationId = sharedNotification.getId();
            } else {
                notificationId = urgentAlertRecorder.saveDeviceAlertNotification(pushDevice.getId(), content,
                        immediateArticles, now).getId();
                deviceNotificationIds.add(notificationId);
            }
            pushDeliveries.add(new PushDelivery(pushDevice, new PushContent(notificationId, NotificationKind.ALERT,
                    content.title(), content.body(), content.articleId(), content.articleCount())));
        }
        return new DeviceDispatchPlan(pushDeliveries, deviceNotificationIds, digestQueueItems);
    }

    private record DeviceDispatchPlan(List<PushDelivery> pushDeliveries, List<Long> deviceNotificationIds,
                                      List<DigestQueueItem> digestQueueItems) {
    }
}
