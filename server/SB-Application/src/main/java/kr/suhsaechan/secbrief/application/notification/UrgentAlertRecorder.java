package kr.suhsaechan.secbrief.application.notification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import kr.suhsaechan.secbrief.member.entity.Notification;
import kr.suhsaechan.secbrief.member.entity.NotificationArticle;
import kr.suhsaechan.secbrief.member.push.PushSendOutcome;
import kr.suhsaechan.secbrief.member.push.PushSendStatus;
import kr.suhsaechan.secbrief.member.repository.DigestItemRepository;
import kr.suhsaechan.secbrief.member.repository.NotificationRepository;
import kr.suhsaechan.secbrief.member.repository.PushTicketRepository;
import kr.suhsaechan.secbrief.member.service.PushDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 기록: 공통·기기 전용 알림함 기록 저장·취소, 알림 처리 표시, 티켓·보류·아침 요약 대기 저장, 무효 기기 삭제 (발송 호출 동안에는 트랜잭션을 잡지 않음)
 */
@Component
@RequiredArgsConstructor
public class UrgentAlertRecorder {

    private final ArticleRepository articleRepository;
    private final PushTicketRepository pushTicketRepository;
    private final NotificationRepository notificationRepository;
    private final PushDeviceService pushDeviceService;
    private final PushDeliveryRecorder pushDeliveryRecorder;
    private final DigestItemRepository digestItemRepository;

    /**
     * 공통 알림함 기록 저장 (알림함 목록에 보임, 발송 전: 푸시 data 에 번호를 실음): 알림 순서대로 기사마다 종류
     */
    @Transactional
    public Notification saveSharedAlertNotification(List<Article> orderedArticles, Instant createdAt) {
        List<NotificationArticle> notificationArticles = new ArrayList<>();
        for (int position = 0; position < orderedArticles.size(); position++) {
            Article article = orderedArticles.get(position);
            notificationArticles.add(new NotificationArticle(position, article.getId(),
                    UrgentAlertPolicy.toAlertCategory(article)));
        }
        UrgentAlertContent content = UrgentAlertPolicy.toContent(orderedArticles);
        return notificationRepository.save(Notification.createAlert(notificationArticles.get(0).getAlertCategory(),
                content.title(), content.body(), notificationArticles, createdAt));
    }

    /**
     * 기기 1대 전용 알림 기록 (관심 기술이 섞여 공통 기록에 없는 묶음, 알림함 목록에 보이지 않음)
     */
    @Transactional
    public Notification saveDeviceAlertNotification(long pushDeviceId, UrgentAlertContent content,
                                                    List<AlertedArticle> alertedArticles, Instant createdAt) {
        List<AlertedArticle> orderedArticles = UrgentAlertPolicy.toOrderedAlertedArticles(alertedArticles);
        List<NotificationArticle> notificationArticles = new ArrayList<>();
        for (int position = 0; position < orderedArticles.size(); position++) {
            notificationArticles.add(new NotificationArticle(position, orderedArticles.get(position).article().getId(),
                    orderedArticles.get(position).alertCategory()));
        }
        return notificationRepository.save(Notification.createForDevice(NotificationKind.ALERT, pushDeviceId,
                orderedArticles.get(0).alertCategory(), content.title(), content.body(), notificationArticles,
                createdAt));
    }

    /**
     * 발송이 전부 실패한 틱: 기록 취소 + 무효 기기 삭제 (기사는 대기로 남아 다음 틱에 새 기록)
     */
    @Transactional
    public void cancelDispatch(Collection<Long> notificationIds, List<PushSendOutcome> outcomes) {
        notificationRepository.deleteAllByIdInBatch(notificationIds);
        pushDeliveryRecorder.deleteUnregisteredDevices(outcomes);
    }

    /**
     * 발송 결과 반영: 기사 알림 처리 표시 > 티켓 저장·무효 기기 삭제 > 보류 저장 > 아침 요약 대기분 > 접수된 건 있으면 기록에 발송 시각
     * notificationIds: 이번 틱의 공통·기기 전용 기록 (없으면 빈 목록)
     */
    @Transactional
    public void recordDispatch(Collection<Long> articleIds, Instant notifiedAt, Collection<Long> notificationIds,
                               PushDeliveryPlan deliveryPlan, List<PushSendOutcome> outcomes,
                               List<DigestQueueItem> digestQueueItems) {
        articleRepository.markUrgentNotified(articleIds, notifiedAt);
        pushDeliveryRecorder.recordOutcomes(outcomes, deliveryPlan.deviceIdByPushToken(), notifiedAt);
        pushDeliveryRecorder.holdDeliveries(deliveryPlan.heldDeliveries(), notifiedAt);
        Set<String> unregisteredPushTokens = outcomes.stream()
                .filter(outcome -> outcome.status() == PushSendStatus.DEVICE_NOT_REGISTERED)
                .map(PushSendOutcome::pushToken)
                .collect(Collectors.toSet());
        Set<Long> unregisteredDeviceIds = unregisteredPushTokens.stream()
                .map(deliveryPlan.deviceIdByPushToken()::get)
                .collect(Collectors.toSet());
        digestQueueItems.stream()
                .filter(digestQueueItem -> !unregisteredDeviceIds.contains(digestQueueItem.pushDeviceId()))
                .forEach(digestQueueItem -> digestItemRepository.insertIgnoringDuplicate(
                        digestQueueItem.pushDeviceId(), digestQueueItem.articleId(), notifiedAt));
        if (!notificationIds.isEmpty() && PushDeliveryRecorder.countByStatus(outcomes, PushSendStatus.ACCEPTED) > 0) {
            pushDeliveryRecorder.markPushed(List.copyOf(notificationIds), notifiedAt);
        }
    }

    /**
     * 접수증 확인 결과 반영: 확인한 티켓 삭제 > 무효 토큰 기기 삭제 > 보관 기간 지난 티켓 정리
     */
    @Transactional
    public void recordReceipts(Collection<Long> checkedTicketIds, Collection<String> unregisteredPushTokens,
                               Instant receiptExpiredBefore) {
        pushTicketRepository.deleteAllByIdInBatch(checkedTicketIds);
        pushDeviceService.deleteByPushTokens(unregisteredPushTokens);
        pushTicketRepository.deleteAllSentBefore(receiptExpiredBefore);
    }
}
