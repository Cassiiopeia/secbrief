package kr.suhsaechan.secbrief.web.scheduler;

import kr.suhsaechan.secbrief.application.ai.ArticleAiProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AI 틱: 5분마다 대기 기사 묶음 처리 (제목 분류·해외 제목 번역)
 * secbrief.ai.enabled=false 면 빈을 만들지 않음 (테스트)
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "secbrief.ai.enabled", havingValue = "true", matchIfMissing = true)
public class ArticleAiScheduler {

    private final ArticleAiProcessService articleAiProcessService;

    @Scheduled(fixedDelayString = "${secbrief.ai.tick-milliseconds}",
            initialDelayString = "${secbrief.ai.initial-delay-milliseconds}")
    public void processPendingArticles() {
        articleAiProcessService.processPendingArticles();
    }
}
