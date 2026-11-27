package kr.suhsaechan.secbrief.web.scheduler;

import kr.suhsaechan.secbrief.application.summary.SourceTextRefetchSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 다시 요약 틱: 1시간마다 브리핑 없는 지난 기사 묶음 (요약 입력 다시 받기 + 운영 API 로 브리핑)
 * 기본 꺼짐: secbrief.summary-refetch.enabled=true 일 때만 빈을 만듦 (밀린 건은 로컬 도구로, docs/summary-harness/backfill.md)
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "secbrief.summary-refetch.enabled", havingValue = "true")
public class SourceTextRefetchSummaryScheduler {

    private final SourceTextRefetchSummaryService sourceTextRefetchSummaryService;

    @Scheduled(fixedDelayString = "${secbrief.summary-refetch.tick-milliseconds}",
            initialDelayString = "${secbrief.summary-refetch.initial-delay-milliseconds}")
    public void processTick() {
        sourceTextRefetchSummaryService.processTick();
    }
}
