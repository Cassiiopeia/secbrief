package kr.suhsaechan.secbrief.advisory.search;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kr.suhsaechan.secbrief.advisory.embedding.EmbeddingVectors;
import kr.suhsaechan.secbrief.advisory.repository.ArticleEmbeddingRepository;
import kr.suhsaechan.secbrief.advisory.repository.StoredEmbedding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

/**
 * 기사 임베딩 메모리 캐시 (의미 검색용): 최근 게시 기사의 정규화 벡터 보관, 질의 벡터와의 코사인 유사도 상위 N건
 * 전체 읽기(처음, 모델 변경, 6시간마다) > 그 사이에는 새로 만든 임베딩만 추가 (요청마다 DB 전체를 읽지 않음)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleEmbeddingIndex {

    // 검색 대상: 게시일이 이 기간 안인 기사
    public static final Duration SEARCHABLE_PUBLISHED_WITHIN = Duration.ofDays(180);
    // 캐시에 올리는 최대 건수, 게시일 최신순 (1024차원 x 4바이트 x 10000건 = 약 40MB)
    public static final int MAXIMUM_INDEXED_ARTICLE_COUNT = 10_000;
    // 새 임베딩 확인 간격 (이 안에 다시 부르면 DB 를 읽지 않음)
    static final Duration INCREMENTAL_REFRESH_INTERVAL = Duration.ofMinutes(1);
    // 전체 다시 읽기 간격 (기간을 벗어난 오래된 기사 정리)
    static final Duration FULL_RELOAD_INTERVAL = Duration.ofHours(6);
    // 추가분 조회 시작을 마지막 확인 시각보다 이만큼 앞당김 (커밋이 늦은 행 누락 방지, 겹친 건 같은 값으로 덮어씀)
    static final Duration INCREMENTAL_OVERLAP = Duration.ofMinutes(1);

    private final ArticleEmbeddingRepository articleEmbeddingRepository;
    private final Clock clock;

    // 기사 ID > 정규화 벡터 (전체 읽기 때 새 맵으로 교체)
    private volatile Map<Long, float[]> normalizedVectorByArticleId = new ConcurrentHashMap<>();
    private String loadedEmbeddingModel;
    private Instant fullyLoadedAt;
    private Instant refreshedAt;

    /**
     * 캐시 최신화: 전체 읽기 조건 3개(처음, 모델 변경, 전체 읽기 간격 경과)면 전체, 아니면 확인 간격이 지났을 때 추가분만
     * 동시에 여러 요청이 와도 한 번만 읽음
     */
    public synchronized void refreshIfStale(String embeddingModel) {
        Instant now = clock.instant();
        if (isFullReloadDue(embeddingModel, now)) {
            reloadAll(embeddingModel, now);
        } else if (!now.isBefore(refreshedAt.plus(INCREMENTAL_REFRESH_INTERVAL))) {
            addCreatedAfter(embeddingModel, now);
        }
    }

    /**
     * 질의 벡터와 유사도가 minimumSimilarity 이상인 기사 ID, 유사도 높은 순 limit 건 (차원이 다른 벡터는 건너뜀)
     */
    public List<Long> findMostSimilarArticleIds(float[] queryVector, int limit, double minimumSimilarity) {
        float[] normalizedQueryVector = EmbeddingVectors.toNormalized(queryVector).orElse(null);
        if (normalizedQueryVector == null) {
            return List.of();
        }
        List<ScoredArticle> scoredArticles = new ArrayList<>();
        normalizedVectorByArticleId.forEach((articleId, normalizedVector) -> {
            if (normalizedVector.length != normalizedQueryVector.length) {
                return;
            }
            double similarity = EmbeddingVectors.toDotProduct(normalizedQueryVector, normalizedVector);
            if (similarity >= minimumSimilarity) {
                scoredArticles.add(new ScoredArticle(articleId, similarity));
            }
        });
        // 같은 유사도는 최근 기사(id 큰 것) 먼저
        return scoredArticles.stream()
                .sorted(Comparator.comparingDouble(ScoredArticle::similarity).reversed()
                        .thenComparing(ScoredArticle::articleId, Comparator.reverseOrder()))
                .limit(limit)
                .map(ScoredArticle::articleId)
                .toList();
    }

    /** 캐시에 올라온 기사 수 */
    public int size() {
        return normalizedVectorByArticleId.size();
    }

    private boolean isFullReloadDue(String embeddingModel, Instant now) {
        return fullyLoadedAt == null
                || !embeddingModel.equals(loadedEmbeddingModel)
                || !now.isBefore(fullyLoadedAt.plus(FULL_RELOAD_INTERVAL));
    }

    private void reloadAll(String embeddingModel, Instant now) {
        Map<Long, float[]> reloadedVectorByArticleId = new ConcurrentHashMap<>();
        putNormalized(reloadedVectorByArticleId, articleEmbeddingRepository.findRecentEmbeddings(
                embeddingModel, now.minus(SEARCHABLE_PUBLISHED_WITHIN), Limit.of(MAXIMUM_INDEXED_ARTICLE_COUNT)));
        normalizedVectorByArticleId = reloadedVectorByArticleId;
        loadedEmbeddingModel = embeddingModel;
        fullyLoadedAt = now;
        refreshedAt = now;
        log.info("[article search] embedding index reloaded : size={}", reloadedVectorByArticleId.size());
    }

    private void addCreatedAfter(String embeddingModel, Instant now) {
        putNormalized(normalizedVectorByArticleId, articleEmbeddingRepository.findEmbeddingsCreatedAfter(
                embeddingModel, now.minus(SEARCHABLE_PUBLISHED_WITHIN), refreshedAt.minus(INCREMENTAL_OVERLAP),
                Limit.of(MAXIMUM_INDEXED_ARTICLE_COUNT)));
        refreshedAt = now;
    }

    // 정규화할 수 없는 벡터(영벡터 등)는 올리지 않음
    private static void putNormalized(Map<Long, float[]> vectorByArticleId, List<StoredEmbedding> storedEmbeddings) {
        for (StoredEmbedding storedEmbedding : storedEmbeddings) {
            EmbeddingVectors.toNormalized(storedEmbedding.embedding())
                    .ifPresent(normalizedVector -> vectorByArticleId.put(storedEmbedding.articleId(), normalizedVector));
        }
    }

    private record ScoredArticle(Long articleId, double similarity) {
    }
}
