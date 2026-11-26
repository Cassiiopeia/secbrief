package kr.suhsaechan.secbrief.web.scheduler;

import kr.suhsaechan.secbrief.advisory.indicator.InternationalAlertIndicatorStore;
import kr.suhsaechan.secbrief.advisory.service.AlertLevelUrgencyExpiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 경보단계 틱 2개 (1시간): 해외 지표 다시 읽기(화면은 보관값만 씀), 게시 7일 지난 KISA 경보단계 글의 긴급 표시 해제
 * secbrief.alert-level.enabled=false 면 빈을 만들지 않음 (테스트는 서비스를 직접 부름)
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "secbrief.alert-level.enabled", havingValue = "true", matchIfMissing = true)
public class AlertLevelScheduler {

    private final InternationalAlertIndicatorStore internationalAlertIndicatorStore;
    private final AlertLevelUrgencyExpiryService alertLevelUrgencyExpiryService;

    @Scheduled(fixedDelayString = "${secbrief.alert-level.tick-milliseconds}",
            initialDelayString = "${secbrief.alert-level.initial-delay-milliseconds}")
    public void refreshInternationalIndicators() {
        internationalAlertIndicatorStore.refreshAll();
    }

    @Scheduled(fixedDelayString = "${secbrief.alert-level.tick-milliseconds}",
            initialDelayString = "${secbrief.alert-level.initial-delay-milliseconds}")
    public void expireAlertLevelUrgency() {
        alertLevelUrgencyExpiryService.expireAlertLevelUrgency();
    }
}
