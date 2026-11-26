package kr.suhsaechan.secbrief.application.notification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.member.push.PushMessage;

/**
 * 발송 계획: 지금 보낼 메시지(+ 토큰 > 기기 번호), 방해 금지 시간이라 보류할 글
 */
public record PushDeliveryPlan(List<PushMessage> pushMessages, Map<String, Long> deviceIdByPushToken,
                               List<PushDelivery> heldDeliveries) {

    /**
     * 기기별 글 > 계획: 그 기기 시간대로 방해 금지 시간 안이면 보류, 아니면 지금 보냄 (KEV 도 예외 없음)
     */
    public static PushDeliveryPlan of(List<PushDelivery> pushDeliveries, Instant now) {
        List<PushMessage> pushMessages = new ArrayList<>();
        Map<String, Long> deviceIdByPushToken = new HashMap<>();
        List<PushDelivery> heldDeliveries = new ArrayList<>();
        for (PushDelivery pushDelivery : pushDeliveries) {
            if (pushDelivery.pushDevice().getQuietHours().isActiveAt(now)) {
                heldDeliveries.add(pushDelivery);
                continue;
            }
            String pushToken = pushDelivery.pushDevice().getPushToken();
            deviceIdByPushToken.put(pushToken, pushDelivery.pushDevice().getId());
            pushMessages.add(pushDelivery.pushContent().toPushMessage(pushToken));
        }
        return new PushDeliveryPlan(pushMessages, deviceIdByPushToken, heldDeliveries);
    }
}
