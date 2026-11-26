package kr.suhsaechan.secbrief.application.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.suhsaechan.secbrief.member.entity.PushTicket;
import kr.suhsaechan.secbrief.member.push.PushReceipt;
import kr.suhsaechan.secbrief.member.push.PushSendStatus;
import kr.suhsaechan.secbrief.member.push.PushSender;
import kr.suhsaechan.secbrief.member.repository.PushTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 접수증 확인 유스케이스: 발송 15분 지난 티켓 > 접수증 조회 > 무효 토큰 기기 삭제, 확인한 티켓 삭제
 * 접수증이 아직 없는 티켓은 남겨 두고, 보관 기간(24시간)이 지나면 정리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushReceiptCheckService {

    // 발송 뒤 접수증을 확인하기까지 기다리는 시간 (Expo 권장 15분)
    static final Duration RECEIPT_WAIT_DURATION = Duration.ofMinutes(15);
    // 접수증 보관 기간 (Expo 가 24시간 뒤 지움)
    static final Duration RECEIPT_RETENTION_DURATION = Duration.ofHours(24);
    // 한 틱에 확인하는 티켓 수 상한
    static final int MAXIMUM_TICKETS_PER_TICK = 1000;

    private final PushTicketRepository pushTicketRepository;
    private final PushSender pushSender;
    private final UrgentAlertRecorder urgentAlertRecorder;
    private final Clock clock;

    public void checkReceipts() {
        if (!pushSender.isAvailable()) {
            return;
        }
        Instant now = clock.instant();
        List<PushTicket> pushTickets = pushTicketRepository.findReceiptCheckTargets(
                now.minus(RECEIPT_WAIT_DURATION), Limit.of(MAXIMUM_TICKETS_PER_TICK));
        Map<String, PushReceipt> receiptByTicketId = pushTickets.isEmpty()
                ? Map.of()
                : pushSender.fetchReceipts(pushTickets.stream().map(PushTicket::getTicketId).toList());

        // 접수증이 온 티켓만 확인 처리 (실패 코드는 종류만 로그로)
        List<Long> checkedTicketIds = new ArrayList<>();
        Set<String> unregisteredPushTokens = new HashSet<>();
        Set<String> failedErrorCodes = new HashSet<>();
        for (PushTicket pushTicket : pushTickets) {
            PushReceipt receipt = receiptByTicketId.get(pushTicket.getTicketId());
            if (receipt == null) {
                continue;
            }
            checkedTicketIds.add(pushTicket.getId());
            if (receipt.status() == PushSendStatus.DEVICE_NOT_REGISTERED) {
                unregisteredPushTokens.add(pushTicket.getPushDevice().getPushToken());
            } else if (receipt.status() == PushSendStatus.FAILED) {
                failedErrorCodes.add(receipt.errorCode());
            }
        }

        urgentAlertRecorder.recordReceipts(checkedTicketIds, unregisteredPushTokens,
                now.minus(RECEIPT_RETENTION_DURATION));
        if (!failedErrorCodes.isEmpty()) {
            // InvalidCredentials, MismatchSenderId 등: 푸시 인증 설정 문제라 운영자가 봐야 함
            log.warn("[push receipt] failed 502 : codes={}", failedErrorCodes);
        }
        if (!checkedTicketIds.isEmpty()) {
            log.info("[push receipt] checked : tickets={}, unregistered={}",
                    checkedTicketIds.size(), unregisteredPushTokens.size());
        }
    }
}
