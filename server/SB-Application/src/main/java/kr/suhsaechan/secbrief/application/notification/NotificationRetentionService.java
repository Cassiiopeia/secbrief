package kr.suhsaechan.secbrief.application.notification;

import java.time.Clock;
import java.time.Duration;
import kr.suhsaechan.secbrief.member.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림함 보관 정리: 긴급 알림 기록은 30일 지나면 삭제 (공지사항은 관리자가 지울 때까지 둠)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRetentionService {

    // 긴급 알림 기록 보관 기간 (30일)
    public static final Duration ALERT_RETENTION_DURATION = Duration.ofDays(30);

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    /**
     * @return 지운 기록 수
     */
    @Transactional
    public int deleteExpiredAlerts() {
        int deletedCount = notificationRepository.deleteAlertsCreatedBefore(clock.instant().minus(ALERT_RETENTION_DURATION));
        if (deletedCount > 0) {
            log.info("[notification retention] deleted expired alerts : count={}", deletedCount);
        }
        return deletedCount;
    }
}
