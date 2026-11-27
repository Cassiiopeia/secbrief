package kr.suhsaechan.secbrief.advisory.search;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.constant.ArticleSearchMode;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSearchResponse;
import kr.suhsaechan.secbrief.advisory.embedding.TextEmbeddingProvider;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.service.ArticleSpecifications;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.ExceptionLogText;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 기사 검색: 요청 확인 > 의미 검색(임베딩 제공자가 준비됐을 때) > 결과가 없거나 실패하면 글자 일치 검색
 * 질의 문자열은 로그에 남기지 않음 (길이만)
 */
@Slf4j
@Service
public class ArticleSearchService {

    // 질의 임베딩을 기다리는 상한 (초): 넘으면 글자 일치 검색으로 대체 (느린 제공자가 검색 응답을 붙잡지 않게)
    private static final long QUERY_EMBEDDING_WAIT_LIMIT_SECONDS = 5;

    private final TextEmbeddingProvider textEmbeddingProvider;
    private final ArticleEmbeddingIndex articleEmbeddingIndex;
    private final ArticleRepository articleRepository;
    // 의미 검색 결과로 받아들일 최소 코사인 유사도 (모델마다 분포가 달라 설정값)
    private final double minimumSimilarity;
    private final Duration queryEmbeddingWaitLimit;
    // 질의 임베딩 호출용 가상 스레드 (대기 상한을 넘기면 호출은 두고 응답만 먼저 돌려줌)
    private final ExecutorService queryEmbeddingExecutor =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("article-search-embedding-", 0).factory());

    @Autowired
    public ArticleSearchService(TextEmbeddingProvider textEmbeddingProvider, ArticleEmbeddingIndex articleEmbeddingIndex,
                                ArticleRepository articleRepository,
                                @Value("${secbrief.embedding.minimum-similarity:0.5}") double minimumSimilarity) {
        this(textEmbeddingProvider, articleEmbeddingIndex, articleRepository, minimumSimilarity,
                Duration.ofSeconds(QUERY_EMBEDDING_WAIT_LIMIT_SECONDS));
    }

    // 대기 상한을 줄여 확인하는 테스트용
    ArticleSearchService(TextEmbeddingProvider textEmbeddingProvider, ArticleEmbeddingIndex articleEmbeddingIndex,
                         ArticleRepository articleRepository, double minimumSimilarity, Duration queryEmbeddingWaitLimit) {
        this.textEmbeddingProvider = textEmbeddingProvider;
        this.articleEmbeddingIndex = articleEmbeddingIndex;
        this.articleRepository = articleRepository;
        this.minimumSimilarity = minimumSimilarity;
        this.queryEmbeddingWaitLimit = queryEmbeddingWaitLimit;
    }

    public ArticleSearchResponse search(String query, Integer size) {
        // 요청 확인 (질의 길이, 결과 크기)
        String searchText = ArticleSearchQueryPolicy.toSearchText(query);
        int searchSize = ArticleSearchQueryPolicy.toSearchSize(size);

        // 의미 검색 (결과가 있을 때만 채택)
        List<Article> semanticArticles = findSemanticArticles(searchText, searchSize);
        if (!semanticArticles.isEmpty()) {
            return toResponse(semanticArticles, ArticleSearchMode.SEMANTIC, searchText);
        }

        // 글자 일치 검색 (최신순)
        List<Article> keywordArticles = articleRepository.findBy(ArticleSpecifications.containsSearchText(searchText),
                fluentQuery -> fluentQuery.sortBy(ArticleSpecifications.NEWEST_FIRST).limit(searchSize).all());
        return toResponse(keywordArticles, ArticleSearchMode.KEYWORD, searchText);
    }

    /**
     * 의미 검색: 질의 임베딩 > 캐시 최신화 > 유사도 상위 기사 (유사도 높은 순)
     * 빈 목록 조건 3개: 제공자 미설정, CVE 번호 질의, 제공자·캐시 실패 (호출부가 글자 일치 검색으로 대체)
     */
    private List<Article> findSemanticArticles(String searchText, int searchSize) {
        if (!textEmbeddingProvider.isAvailable() || ArticleSearchQueryPolicy.isCveIdQuery(searchText)) {
            return List.of();
        }
        try {
            float[] queryVector = embedQueryWithinWaitLimit(searchText);
            articleEmbeddingIndex.refreshIfStale(textEmbeddingProvider.getModelName());
            return findArticlesInOrder(
                    articleEmbeddingIndex.findMostSimilarArticleIds(queryVector, searchSize, minimumSimilarity));
        } catch (RuntimeException exception) {
            log.warn("[article search] semantic failed 502 : fallback to keyword : queryLength={}, cause={}",
                    searchText.length(), ExceptionLogText.toLogCause(exception));
            return List.of();
        }
    }

    // 질의 1건 임베딩: 대기 상한을 넘거나 제공자가 실패하면 AI_API_ERROR
    private float[] embedQueryWithinWaitLimit(String searchText) {
        CompletableFuture<List<float[]>> embeddingFuture = CompletableFuture.supplyAsync(
                () -> textEmbeddingProvider.embed(List.of(searchText)), queryEmbeddingExecutor);
        try {
            List<float[]> embeddings = embeddingFuture.get(queryEmbeddingWaitLimit.toMillis(), TimeUnit.MILLISECONDS);
            if (embeddings.size() != 1) {
                throw new CustomException(ErrorCode.AI_API_ERROR, "embedding count mismatch");
            }
            return embeddings.get(0);
        } catch (TimeoutException exception) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "query embedding timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.AI_API_ERROR, "query embedding interrupted");
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof CustomException customException) {
                throw customException;
            }
            throw new CustomException(ErrorCode.AI_API_ERROR,
                    "query embedding failed : " + exception.getCause().getClass().getSimpleName());
        }
    }

    // ID 순서(유사도 높은 순)대로 기사 조회 (그 사이 지워진 기사는 빠짐)
    private List<Article> findArticlesInOrder(List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Article> articleById = articleRepository.findAllById(articleIds).stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
        return articleIds.stream().map(articleById::get).filter(Objects::nonNull).toList();
    }

    private static ArticleSearchResponse toResponse(List<Article> articles, ArticleSearchMode searchMode, String searchText) {
        log.info("[article search] searched : mode={}, queryLength={}, results={}",
                searchMode, searchText.length(), articles.size());
        return new ArticleSearchResponse(articles.stream().map(ArticleResponse::from).toList(), searchMode);
    }
}
