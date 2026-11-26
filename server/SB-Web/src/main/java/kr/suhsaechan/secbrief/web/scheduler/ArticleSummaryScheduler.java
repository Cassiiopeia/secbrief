package kr.suhsaechan.secbrief.web.scheduler;

import kr.suhsaechan.secbrief.application.summary.ArticleSummaryProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 요약 틱: 3분마다 대기 기사 묶음 처리 (앱 안 상세의 AI 요약·핵심 정리·대응 안내)
 * secbrief.summary.enabled=false 면 빈을 만들지 않음 (테스트)
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "secbrief.summary.enabled", havingValue = "true", matchIfMissing = true)
public class ArticleSummaryScheduler {

    private final ArticleSummaryProcessService articleSummaryProcessService;

    @Scheduled(fixedDelayString = "${secbrief.summary.tick-milliseconds}",
            initialDelayString = "${secbrief.summary.initial-delay-milliseconds}")
    public void processPendingSummaries() {
        articleSummaryProcessService.processPendingSummaries();
    }
}
