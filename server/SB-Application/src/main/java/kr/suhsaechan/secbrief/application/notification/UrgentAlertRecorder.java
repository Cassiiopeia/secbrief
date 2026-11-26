package kr.suhsaechan.secbrief.application.notification;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.entity.Notification;
import kr.suhsaechan.secbrief.member.entity.NotificationArticle;
import kr.suhsaechan.secbrief.member.push.PushSendOutcome;
import kr.suhsaechan.secbrief.member.push.PushSendStatus;
import kr.suhsaechan.secbrief.member.repository.NotificationRepository;
import kr.suhsaechan.secbrief.member.repository.PushTicketRepository;
import kr.suhsaechan.secbrief.member.service.PushDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 긴급 알림 기록: 알림함 기록 저장·취소, 발송 처리 표시, 티켓·보류 저장, 무효 기기 삭제 (발송 호출 동안에는 트랜잭션을 잡지 않음)
 */
@Component
@RequiredArgsConstructor
public class UrgentAlertRecorder {

    private final ArticleRepository articleRepository;
    private final PushTicketRepository pushTicketRepository;
    private final NotificationRepository notificationRepository;
    private final PushDeviceService pushDeviceService;
    private final PushDeliveryRecorder pushDeliveryRecorder;

    /**
     * 알림함 기록 저장 (발송 전: 푸시 data 에 번호를 실음)
     */
    @Transactional
    public Notification saveAlertNotification(UrgentAlertContent content, AlertCategory leadAlertCategory,
                                              List<NotificationArticle> articles, Instant createdAt) {
        return notificationRepository.save(Notification.createAlert(leadAlertCategory, content.title(),
                content.body(), articles, createdAt));
    }

    /**
     * 발송이 전부 실패한 틱: 기록 취소 + 무효 기기 삭제 (기사는 대기로 남아 다음 틱에 새 기록)
     */
    @Transactional
    public void cancelDispatch(Long notificationId, List<PushSendOutcome> outcomes) {
        notificationRepository.deleteById(notificationId);
        pushDeliveryRecorder.deleteUnregisteredDevices(outcomes);
    }

    /**
     * 발송 결과 반영: 기사 발송 처리 표시 > 티켓 저장·무효 기기 삭제 > 보류 저장 > 접수된 건 있으면 기록에 발송 시각
     * notificationId: 알릴 기사가 없던 틱이면 null
     */
    @Transactional
    public void recordDispatch(Collection<Long> articleIds, Instant notifiedAt, Long notificationId,
                               PushDeliveryPlan deliveryPlan, List<PushSendOutcome> outcomes) {
        articleRepository.markUrgentNotified(articleIds, notifiedAt);
        pushDeliveryRecorder.recordOutcomes(outcomes, deliveryPlan.deviceIdByPushToken(), notifiedAt);
        pushDeliveryRecorder.holdDeliveries(deliveryPlan.heldDeliveries(), notifiedAt);
        if (notificationId != null && PushDeliveryRecorder.countByStatus(outcomes, PushSendStatus.ACCEPTED) > 0) {
            pushDeliveryRecorder.markPushed(List.of(notificationId), notifiedAt);
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
