package kr.suhsaechan.secbrief.web.scheduler;

import kr.suhsaechan.secbrief.application.notification.PushReceiptCheckService;
import kr.suhsaechan.secbrief.application.notification.MorningDigestService;
import kr.suhsaechan.secbrief.application.notification.QuietHoursDigestService;
import kr.suhsaechan.secbrief.application.notification.UrgentAlertDispatchService;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 알림 틱 2개: 5분마다 알림 발송 + 방해 금지 시간 끝난 기기에 보류분 발송 + 요약 시각이 된 기기에 아침 요약, 15분마다 접수증 확인 (무효 토큰 정리)
 * secbrief.push.enabled=false 면 빈을 만들지 않음 (테스트), 설정 저장소 URGENT_ALERT_ENABLED 가 false 면 발송 틱만 쉼 (접수증 확인은 계속)
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "secbrief.push.enabled", havingValue = "true", matchIfMissing = true)
public class UrgentAlertScheduler {

    private final UrgentAlertDispatchService urgentAlertDispatchService;
    private final PushReceiptCheckService pushReceiptCheckService;
    private final QuietHoursDigestService quietHoursDigestService;
    private final MorningDigestService morningDigestService;
    private final SystemConfigService systemConfigService;

    @Scheduled(fixedDelayString = "${secbrief.push.alert-tick-milliseconds}",
            initialDelayString = "${secbrief.push.alert-initial-delay-milliseconds}")
    public void dispatchPendingAlerts() {
        if (!systemConfigService.getBoolean(ConfigKey.URGENT_ALERT_ENABLED)) {
            return;
        }
        urgentAlertDispatchService.dispatchPendingAlerts();
        quietHoursDigestService.sendHeldPushesAfterQuietHours();
        morningDigestService.sendDueDigests();
    }

    @Scheduled(fixedDelayString = "${secbrief.push.receipt-tick-milliseconds}",
            initialDelayString = "${secbrief.push.receipt-initial-delay-milliseconds}")
    public void checkReceipts() {
        pushReceiptCheckService.checkReceipts();
    }
}
