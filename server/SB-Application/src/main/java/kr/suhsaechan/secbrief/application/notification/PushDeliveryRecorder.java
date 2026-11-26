package kr.suhsaechan.secbrief.application.notification;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.member.entity.HeldPush;
import kr.suhsaechan.secbrief.member.entity.PushTicket;
import kr.suhsaechan.secbrief.member.push.PushSendOutcome;
import kr.suhsaechan.secbrief.member.push.PushSendStatus;
import kr.suhsaechan.secbrief.member.repository.HeldPushRepository;
import kr.suhsaechan.secbrief.member.repository.NotificationRepository;
import kr.suhsaechan.secbrief.member.repository.PushDeviceRepository;
import kr.suhsaechan.secbrief.member.repository.PushTicketRepository;
import kr.suhsaechan.secbrief.member.service.PushDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 푸시 발송 결과 기록 공통 (긴급 알림·공지·방해 금지 모음): 티켓 저장, 무효 기기 삭제, 보류 저장, 알림함 발송 시각
 * 호출부 트랜잭션에 합류 (없으면 메서드마다 새로)
 */
@Component
@RequiredArgsConstructor
public class PushDeliveryRecorder {

    private final PushDeviceRepository pushDeviceRepository;
    private final PushTicketRepository pushTicketRepository;
    private final HeldPushRepository heldPushRepository;
    private final NotificationRepository notificationRepository;
    private final PushDeviceService pushDeviceService;

    /**
     * 발송 결과 반영: 접수된 건 티켓 저장 > 무효 토큰 기기 삭제
     * deviceIdByPushToken: 이번에 보낸 기기 (토큰 > 기기 번호)
     */
    @Transactional
    public void recordOutcomes(List<PushSendOutcome> outcomes, Map<String, Long> deviceIdByPushToken, Instant sentAt) {
        pushTicketRepository.saveAll(outcomes.stream()
                .filter(outcome -> outcome.status() == PushSendStatus.ACCEPTED)
                .map(outcome -> PushTicket.create(
                        pushDeviceRepository.getReferenceById(deviceIdByPushToken.get(outcome.pushToken())),
                        outcome.ticketId(), sentAt))
                .toList());
        deleteUnregisteredDevices(outcomes);
    }

    /**
     * 무효 토큰 기기만 삭제 (발송이 전부 실패해 다음에 다시 보낼 때)
     */
    @Transactional
    public int deleteUnregisteredDevices(List<PushSendOutcome> outcomes) {
        return pushDeviceService.deleteByPushTokens(outcomes.stream()
                .filter(outcome -> outcome.status() == PushSendStatus.DEVICE_NOT_REGISTERED)
                .map(PushSendOutcome::pushToken)
                .toList());
    }

    /**
     * 방해 금지 시간이라 보류한 글 저장 (끝나면 보냄)
     */
    @Transactional
    public void holdDeliveries(List<PushDelivery> heldDeliveries, Instant heldAt) {
        heldPushRepository.saveAll(heldDeliveries.stream()
                .map(heldDelivery -> HeldPush.create(heldDelivery.pushDevice(),
                        heldDelivery.pushContent().notificationId(), heldDelivery.pushContent().notificationKind(),
                        heldDelivery.pushContent().title(), heldDelivery.pushContent().body(),
                        heldDelivery.pushContent().articleId(), heldDelivery.pushContent().articleCount(), heldAt))
                .toList());
    }

    /**
     * 알림함 기록에 처음 보낸 시각 표시 (이미 있으면 그대로)
     */
    @Transactional
    public void markPushed(Collection<Long> notificationIds, Instant pushedAt) {
        notificationRepository.findAllById(notificationIds).forEach(notification -> notification.markPushed(pushedAt));
    }

    /**
     * 보류분 발송 결과 반영: 티켓·무효 기기 > 보낸 보류분 삭제 > 알림함 기록 발송 시각
     */
    @Transactional
    public void recordHeldPushesSent(List<PushSendOutcome> outcomes, Map<String, Long> deviceIdByPushToken,
                                     List<HeldPush> sentHeldPushes, Instant sentAt) {
        recordOutcomes(outcomes, deviceIdByPushToken, sentAt);
        heldPushRepository.deleteAllByIdInBatch(sentHeldPushes.stream().map(HeldPush::getId).toList());
        markPushed(sentHeldPushes.stream().map(HeldPush::getNotificationId).distinct().toList(), sentAt);
    }

    public static int countByStatus(List<PushSendOutcome> outcomes, PushSendStatus status) {
        return (int) outcomes.stream().filter(outcome -> outcome.status() == status).count();
    }
}
