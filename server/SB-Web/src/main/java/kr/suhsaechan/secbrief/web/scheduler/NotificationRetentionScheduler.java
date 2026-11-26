package kr.suhsaechan.secbrief.web.scheduler;

import kr.suhsaechan.secbrief.application.notification.NotificationRetentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 알림함 보관 정리 틱 (6시간): 30일 지난 긴급 알림 기록 삭제
 * secbrief.notification.retention-enabled=false 면 빈을 만들지 않음 (테스트는 서비스를 직접 부름)
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "secbrief.notification.retention-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationRetentionScheduler {

    private final NotificationRetentionService notificationRetentionService;

    @Scheduled(fixedDelayString = "${secbrief.notification.retention-tick-milliseconds}",
            initialDelayString = "${secbrief.notification.retention-initial-delay-milliseconds}")
    public void deleteExpiredAlerts() {
        notificationRetentionService.deleteExpiredAlerts();
    }
}
