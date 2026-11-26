package kr.suhsaechan.secbrief.application.notification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.application.notification.MorningDigestService.DigestDelivery;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import kr.suhsaechan.secbrief.member.entity.Notification;
import kr.suhsaechan.secbrief.member.entity.NotificationArticle;
import kr.suhsaechan.secbrief.member.push.PushSendOutcome;
import kr.suhsaechan.secbrief.member.push.PushSendStatus;
import kr.suhsaechan.secbrief.member.repository.DigestItemRepository;
import kr.suhsaechan.secbrief.member.repository.NotificationRepository;
import kr.suhsaechan.secbrief.member.repository.PushDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아침 요약 기록: 기기 전용 DIGEST 기록 저장, 발송 결과 반영(접수: 대기분 삭제·오늘 보냄, 실패: 기록 삭제), 오래된 대기분 정리
 */
@Component
@RequiredArgsConstructor
public class MorningDigestRecorder {

    private final NotificationRepository notificationRepository;
    private final DigestItemRepository digestItemRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final PushDeliveryRecorder pushDeliveryRecorder;

    @Transactional
    public int deleteExpiredItems(Instant queuedBefore) {
        return digestItemRepository.deleteQueuedBefore(queuedBefore);
    }

    /** 기록 저장: 기사 순서는 대기 순서, 종류는 모두 관심 기술 */
    @Transactional
    public Notification saveDigestNotification(long pushDeviceId, UrgentAlertContent content, List<Article> articles,
                                               Instant createdAt) {
        List<NotificationArticle> notificationArticles = new ArrayList<>();
        for (int position = 0; position < articles.size(); position++) {
            notificationArticles.add(new NotificationArticle(position, articles.get(position).getId(),
                    AlertCategory.INTEREST));
        }
        return notificationRepository.save(Notification.createForDevice(NotificationKind.DIGEST, pushDeviceId,
                AlertCategory.INTEREST, content.title(), content.body(), notificationArticles, createdAt));
    }

    /**
     * 결과 반영: 티켓 저장·무효 기기 삭제 > 접수된 기기는 대기분 삭제·오늘 보냄·발송 시각, 실패한 기기는 기록 삭제
     */
    @Transactional
    public void recordOutcomes(List<PushSendOutcome> outcomes, Map<String, DigestDelivery> deliveryByPushToken,
                               Instant sentAt) {
        Map<String, Long> deviceIdByPushToken = new HashMap<>();
        deliveryByPushToken.forEach((pushToken, delivery) -> deviceIdByPushToken.put(pushToken, delivery.pushDeviceId()));
        pushDeliveryRecorder.recordOutcomes(outcomes, deviceIdByPushToken, sentAt);
        for (PushSendOutcome outcome : outcomes) {
            DigestDelivery delivery = deliveryByPushToken.get(outcome.pushToken());
            if (delivery == null) {
                continue;
            }
            if (outcome.status() == PushSendStatus.ACCEPTED) {
                digestItemRepository.deleteSent(delivery.pushDeviceId(), delivery.articleIds());
                pushDeviceRepository.findById(delivery.pushDeviceId()).ifPresent(pushDevice -> pushDevice.markDigestSent(sentAt));
                pushDeliveryRecorder.markPushed(List.of(delivery.notificationId()), sentAt);
            } else if (outcome.status() == PushSendStatus.FAILED) {
                notificationRepository.deleteById(delivery.notificationId());
            }
        }
    }
}
