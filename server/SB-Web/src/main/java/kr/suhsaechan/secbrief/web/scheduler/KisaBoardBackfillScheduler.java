package kr.suhsaechan.secbrief.web.scheduler;

import kr.suhsaechan.secbrief.application.collect.KisaBoardBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * KISA 지난 글 채우기 틱 (6시간): 채우기 전인 KISA 출처가 있으면 백그라운드로 시작 (끝난 출처는 다시 안 함)
 * secbrief.collect.enabled=false 면 빈을 만들지 않음 (수집과 함께 꺼짐, 테스트)
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "secbrief.collect.enabled", havingValue = "true", matchIfMissing = true)
public class KisaBoardBackfillScheduler {

    private final KisaBoardBackfillService kisaBoardBackfillService;

    @Scheduled(fixedDelayString = "${secbrief.collect.kisa-backfill-tick-milliseconds}",
            initialDelayString = "${secbrief.collect.kisa-backfill-initial-delay-milliseconds}")
    public void startKisaBackfill() {
        kisaBoardBackfillService.startBackfillIfIdle();
    }
}
