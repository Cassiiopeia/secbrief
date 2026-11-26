package kr.suhsaechan.secbrief.application.notification;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.member.entity.PushTicket;
import kr.suhsaechan.secbrief.member.push.PushSendOutcome;
import kr.suhsaechan.secbrief.member.push.PushSendStatus;
import kr.suhsaechan.secbrief.member.repository.PushDeviceRepository;
import kr.suhsaechan.secbrief.member.repository.PushTicketRepository;
import kr.suhsaechan.secbrief.member.service.PushDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 긴급 알림 기록: 발송 처리 표시, 티켓 저장, 무효 기기 삭제 (발송 호출 동안에는 트랜잭션을 잡지 않음)
 */
@Component
@RequiredArgsConstructor
public class UrgentAlertRecorder {

    private final ArticleRepository articleRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final PushTicketRepository pushTicketRepository;
    private final PushDeviceService pushDeviceService;

    /**
     * 발송 결과 반영: 기사 발송 처리 표시 > 접수된 건 티켓 저장 > 무효 토큰 기기 삭제
     * deviceIdByPushToken: 이번 틱에 보낸 기기 (토큰 > 기기 번호)
     */
    @Transactional
    public void recordDispatch(Collection<Long> articleIds, Instant notifiedAt,
                               List<PushSendOutcome> outcomes, Map<String, Long> deviceIdByPushToken) {
        articleRepository.markUrgentNotified(articleIds, notifiedAt);
        pushTicketRepository.saveAll(outcomes.stream()
                .filter(outcome -> outcome.status() == PushSendStatus.ACCEPTED)
                .map(outcome -> PushTicket.create(
                        pushDeviceRepository.getReferenceById(deviceIdByPushToken.get(outcome.pushToken())),
                        outcome.ticketId(), notifiedAt))
                .toList());
        deleteUnregisteredDevices(outcomes);
    }

    /**
     * 무효 토큰 기기만 삭제 (발송이 전부 실패해 기사를 대기로 남길 때)
     */
    @Transactional
    public int deleteUnregisteredDevices(List<PushSendOutcome> outcomes) {
        return pushDeviceService.deleteByPushTokens(outcomes.stream()
                .filter(outcome -> outcome.status() == PushSendStatus.DEVICE_NOT_REGISTERED)
                .map(PushSendOutcome::pushToken)
                .toList());
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
