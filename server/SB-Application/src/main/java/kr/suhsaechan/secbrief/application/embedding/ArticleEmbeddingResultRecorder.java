package kr.suhsaechan.secbrief.application.embedding;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.entity.ArticleEmbedding;
import kr.suhsaechan.secbrief.advisory.repository.ArticleEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 임베딩 결과 기록: 묶음 1회분을 한 트랜잭션으로 저장 (이미 있으면 교체: 모델을 바꾼 경우)
 * 제공자 호출 동안에는 트랜잭션을 잡지 않음
 */
@Component
@RequiredArgsConstructor
public class ArticleEmbeddingResultRecorder {

    private final ArticleEmbeddingRepository articleEmbeddingRepository;
    private final Clock clock;

    @Transactional
    public void recordEmbeddings(String embeddingModel, Map<Long, float[]> embeddingByArticleId) {
        Instant createdAt = clock.instant();
        Map<Long, ArticleEmbedding> existingEmbeddingByArticleId =
                articleEmbeddingRepository.findAllById(embeddingByArticleId.keySet()).stream()
                        .collect(Collectors.toMap(ArticleEmbedding::getArticleId, Function.identity()));
        embeddingByArticleId.forEach((articleId, embedding) -> {
            ArticleEmbedding existingEmbedding = existingEmbeddingByArticleId.get(articleId);
            if (existingEmbedding == null) {
                articleEmbeddingRepository.save(ArticleEmbedding.create(articleId, embeddingModel, embedding, createdAt));
            } else {
                existingEmbedding.replace(embeddingModel, embedding, createdAt);
            }
        });
    }
}
