package kr.suhsaechan.secbrief.advisory.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import kr.suhsaechan.secbrief.advisory.repository.ArticleEmbeddingRepository;
import kr.suhsaechan.secbrief.advisory.repository.StoredEmbedding;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

/**
 * 임베딩 메모리 캐시: 처음엔 전체 읽기, 확인 간격 안에는 DB 를 읽지 않음, 그 뒤 추가분만, 모델 변경·6시간 경과면 전체 다시
 * 유사도 높은 순·최소 유사도·건수 상한, 차원이 다른 벡터와 영벡터는 건너뜀
 */
class ArticleEmbeddingIndexTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
    private static final String MODEL = "test-embedding-model";
    private static final Limit INDEX_LIMIT = Limit.of(ArticleEmbeddingIndex.MAXIMUM_INDEXED_ARTICLE_COUNT);

    private final ArticleEmbeddingRepository repository = mock(ArticleEmbeddingRepository.class);
    private final AtomicReference<Instant> currentTime = new AtomicReference<>(NOW);
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentTime.get();
        }
    };
    private final ArticleEmbeddingIndex index = new ArticleEmbeddingIndex(repository, clock);

    @Test
    void ordersBySimilarityAndDropsBelowMinimum() {
        givenRecentEmbeddings(stored(1L, 1f, 0f), stored(2L, 0.8f, 0.6f), stored(3L, 0f, 1f), stored(4L, -1f, 0f));
        index.refreshIfStale(MODEL);

        // 질의 (1, 0) 과의 유사도: 1번 1.0, 2번 0.8, 3번 0.0, 4번 -1.0
        assertThat(index.findMostSimilarArticleIds(new float[] {2f, 0f}, 10, 0.5)).containsExactly(1L, 2L);
        assertThat(index.findMostSimilarArticleIds(new float[] {2f, 0f}, 10, -0.5)).containsExactly(1L, 2L, 3L);
        assertThat(index.findMostSimilarArticleIds(new float[] {2f, 0f}, 1, 0.5)).containsExactly(1L);
    }

    @Test
    void skipsVectorsWithOtherDimensionAndZeroVectors() {
        givenRecentEmbeddings(stored(1L, 1f, 0f), stored(2L, 1f, 0f, 0f), stored(3L, 0f, 0f));
        index.refreshIfStale(MODEL);

        assertThat(index.size()).isEqualTo(2);
        assertThat(index.findMostSimilarArticleIds(new float[] {1f, 0f}, 10, 0.0)).containsExactly(1L);
        assertThat(index.findMostSimilarArticleIds(new float[] {0f, 0f}, 10, 0.0)).isEmpty();
    }

    @Test
    void loadsAllOnceThenReadsNothingWithinRefreshInterval() {
        givenRecentEmbeddings(stored(1L, 1f, 0f));

        index.refreshIfStale(MODEL);
        currentTime.set(NOW.plusSeconds(30));
        index.refreshIfStale(MODEL);

        verify(repository, times(1)).findRecentEmbeddings(
                MODEL, NOW.minus(ArticleEmbeddingIndex.SEARCHABLE_PUBLISHED_WITHIN), INDEX_LIMIT);
        verify(repository, never()).findEmbeddingsCreatedAfter(any(), any(), any(), any());
    }

    @Test
    void addsOnlyNewEmbeddingsAfterRefreshInterval() {
        givenRecentEmbeddings(stored(1L, 1f, 0f));
        index.refreshIfStale(MODEL);
        Instant later = NOW.plus(Duration.ofMinutes(2));
        currentTime.set(later);
        // 마지막 확인 시각보다 1분 앞에서부터 조회 (커밋이 늦은 행 누락 방지), 다시 만든 1번은 덮어씀
        when(repository.findEmbeddingsCreatedAfter(eq(MODEL), eq(later.minus(ArticleEmbeddingIndex.SEARCHABLE_PUBLISHED_WITHIN)),
                eq(NOW.minus(ArticleEmbeddingIndex.INCREMENTAL_OVERLAP)), eq(INDEX_LIMIT)))
                .thenReturn(List.of(stored(1L, 0f, 1f), stored(5L, 1f, 0f)));

        index.refreshIfStale(MODEL);

        verify(repository, times(1)).findRecentEmbeddings(any(), any(), any());
        assertThat(index.size()).isEqualTo(2);
        assertThat(index.findMostSimilarArticleIds(new float[] {1f, 0f}, 10, 0.5)).containsExactly(5L);
    }

    @Test
    void reloadsAllWhenModelChangesOrFullReloadIntervalPasses() {
        givenRecentEmbeddings(stored(1L, 1f, 0f));
        index.refreshIfStale(MODEL);
        when(repository.findRecentEmbeddings(eq("other-model"), any(), any())).thenReturn(List.of(stored(9L, 1f, 0f)));

        index.refreshIfStale("other-model");

        assertThat(index.findMostSimilarArticleIds(new float[] {1f, 0f}, 10, 0.5)).containsExactly(9L);

        currentTime.set(NOW.plus(ArticleEmbeddingIndex.FULL_RELOAD_INTERVAL));
        index.refreshIfStale("other-model");

        verify(repository, times(2)).findRecentEmbeddings(eq("other-model"), any(), any());
    }

    private void givenRecentEmbeddings(StoredEmbedding... storedEmbeddings) {
        when(repository.findRecentEmbeddings(eq(MODEL), any(), any())).thenReturn(List.of(storedEmbeddings));
    }

    private static StoredEmbedding stored(Long articleId, float... embedding) {
        return new StoredEmbedding(articleId, embedding, NOW);
    }
}
