package kr.suhsaechan.secbrief.web.scheduler;

import kr.suhsaechan.secbrief.application.image.ArticleImageProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 이미지 틱: 2분마다 대기 기사 묶음 처리 (대표 이미지 내려받기 > 줄이기 > 저장)
 * secbrief.image.enabled=false 면 빈을 만들지 않음 (테스트)
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "secbrief.image.enabled", havingValue = "true", matchIfMissing = true)
public class ArticleImageScheduler {

    private final ArticleImageProcessService articleImageProcessService;

    @Scheduled(fixedDelayString = "${secbrief.image.tick-milliseconds}",
            initialDelayString = "${secbrief.image.initial-delay-milliseconds}")
    public void processPendingImages() {
        articleImageProcessService.processPendingImages();
    }
}
