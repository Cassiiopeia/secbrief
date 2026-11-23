package kr.suhsaechan.secbrief.web.scheduler;

import kr.suhsaechan.secbrief.application.collect.ArticleCollectService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 수집 틱: 1분마다 주기가 된 출처만 수집 (출처별 주기는 source.interval_minutes)
 * secbrief.collect.enabled=false 면 빈을 만들지 않음 (테스트)
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "secbrief.collect.enabled", havingValue = "true", matchIfMissing = true)
public class ArticleCollectScheduler {

    private final ArticleCollectService articleCollectService;

    @Scheduled(fixedDelayString = "${secbrief.collect.tick-milliseconds}",
            initialDelayString = "${secbrief.collect.initial-delay-milliseconds}")
    public void collectDueSources() {
        articleCollectService.collectDueSources();
    }
}
