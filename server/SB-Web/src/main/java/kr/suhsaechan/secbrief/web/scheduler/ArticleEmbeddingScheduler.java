package kr.suhsaechan.secbrief.web.scheduler;

import kr.suhsaechan.secbrief.application.embedding.ArticleEmbeddingProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 임베딩 틱: 5분마다 임베딩이 없는 기사 묶음 처리 (의미 검색용 제목 임베딩)
 * secbrief.embedding.enabled=false 면 빈을 만들지 않음 (테스트)
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "secbrief.embedding.enabled", havingValue = "true", matchIfMissing = true)
public class ArticleEmbeddingScheduler {

    private final ArticleEmbeddingProcessService articleEmbeddingProcessService;

    @Scheduled(fixedDelayString = "${secbrief.embedding.tick-milliseconds}",
            initialDelayString = "${secbrief.embedding.initial-delay-milliseconds}")
    public void processMissingEmbeddings() {
        articleEmbeddingProcessService.processMissingEmbeddings();
    }
}
