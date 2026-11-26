package kr.suhsaechan.secbrief.application.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.member.entity.HeldPush;
import kr.suhsaechan.secbrief.member.entity.PushDevice;
import kr.suhsaechan.secbrief.member.push.PushMessage;
import kr.suhsaechan.secbrief.member.push.PushSendOutcome;
import kr.suhsaechan.secbrief.member.push.PushSendStatus;
import kr.suhsaechan.secbrief.member.push.PushSender;
import kr.suhsaechan.secbrief.member.repository.HeldPushRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 방해 금지 시간 끝난 기기에 보류 푸시 보내기: 보류분 조회 > 시간이 끝난 기기만 1건씩 (모음 또는 그대로) > 발송 > 기록
 * 접수되면 보류분 삭제, 실패하면 남겨 다음 틱에 다시, 무효 토큰이면 기기 삭제 (보류분도 함께)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuietHoursDigestService {

    // 한 틱에 읽는 보류 푸시 수 상한 (넘는 분량은 다음 틱)
    static final int MAXIMUM_HELD_PUSHES_PER_TICK = 5000;

    private final HeldPushRepository heldPushRepository;
    private final PushSender pushSender;
    private final PushDeliveryRecorder pushDeliveryRecorder;
    private final Clock clock;

    /**
     * @return 보낸 기기 수 (접수 기준)
     */
    public int sendHeldPushesAfterQuietHours() {
        if (!pushSender.isAvailable()) {
            return 0;
        }
        Instant now = clock.instant();
        Map<PushDevice, List<HeldPush>> heldPushesByDevice = toHeldPushesOfEndedDevices(now);
        if (heldPushesByDevice.isEmpty()) {
            return 0;
        }

        List<PushMessage> pushMessages = new ArrayList<>();
        Map<String, Long> deviceIdByPushToken = new HashMap<>();
        Map<String, List<HeldPush>> heldPushesByPushToken = new HashMap<>();
        heldPushesByDevice.forEach((pushDevice, heldPushes) -> {
            pushMessages.add(QuietHoursDigestPolicy.toPushMessage(pushDevice.getPushToken(), heldPushes));
            deviceIdByPushToken.put(pushDevice.getPushToken(), pushDevice.getId());
            heldPushesByPushToken.put(pushDevice.getPushToken(), heldPushes);
        });

        List<PushSendOutcome> outcomes = pushSender.send(pushMessages);
        List<HeldPush> sentHeldPushes = outcomes.stream()
                .filter(outcome -> outcome.status() == PushSendStatus.ACCEPTED)
                .flatMap(outcome -> heldPushesByPushToken.get(outcome.pushToken()).stream())
                .toList();
        pushDeliveryRecorder.recordHeldPushesSent(outcomes, deviceIdByPushToken, sentHeldPushes, now);
        int acceptedCount = PushDeliveryRecorder.countByStatus(outcomes, PushSendStatus.ACCEPTED);
        log.info("[quiet hours] sent held pushes : devices={}, accepted={}, failed={}", pushMessages.size(),
                acceptedCount, PushDeliveryRecorder.countByStatus(outcomes, PushSendStatus.FAILED));
        return acceptedCount;
    }

    // 기기별 보류분 (지금 방해 금지 시간 안인 기기는 뺌, 보류 순서 유지)
    private Map<PushDevice, List<HeldPush>> toHeldPushesOfEndedDevices(Instant now) {
        Map<PushDevice, List<HeldPush>> heldPushesByDevice = new LinkedHashMap<>();
        for (HeldPush heldPush : heldPushRepository.findAllWithPushDevice(Limit.of(MAXIMUM_HELD_PUSHES_PER_TICK))) {
            if (!heldPush.getPushDevice().getQuietHours().isActiveAt(now)) {
                heldPushesByDevice.computeIfAbsent(heldPush.getPushDevice(), pushDevice -> new ArrayList<>()).add(heldPush);
            }
        }
        return heldPushesByDevice;
    }
}
