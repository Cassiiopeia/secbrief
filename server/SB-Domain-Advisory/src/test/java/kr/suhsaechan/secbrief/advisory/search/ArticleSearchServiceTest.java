package kr.suhsaechan.secbrief.advisory.search;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleSearchMode;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSearchResponse;
import kr.suhsaechan.secbrief.advisory.embedding.TextEmbeddingProvider;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 기사 검색 흐름: 의미 검색 결과가 있으면 SEMANTIC(유사도 순), 없거나 제공자 미설정·실패·시간 초과·CVE 번호 질의면 KEYWORD
 * 질의·크기 검증 (가짜 제공자·캐시·저장소, 글자 일치 조건 자체는 DB 테스트가 확인)
 */
class ArticleSearchServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
    private static final String MODEL = "test-embedding-model";
    private static final double MINIMUM_SIMILARITY = 0.5;
    private static final float[] QUERY_VECTOR = {1f, 0f};

    private final TextEmbeddingProvider provider = mock(TextEmbeddingProvider.class);
    private final ArticleEmbeddingIndex index = mock(ArticleEmbeddingIndex.class);
    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final ArticleSearchService service = new ArticleSearchService(
            provider, index, articleRepository, MINIMUM_SIMILARITY, Duration.ofMillis(300));

    private final Article keywordMatchedArticle = createArticle(1L, "병원 랜섬웨어 피해");
    private final Article firstSemanticArticle = createArticle(2L, "의료기관 해킹 사고");
    private final Article secondSemanticArticle = createArticle(3L, "병원 전산망 마비");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void givenKeywordResultAndAvailableProvider() {
        when(articleRepository.findBy(any(Specification.class), any())).thenReturn(List.of(keywordMatchedArticle));
        when(provider.isAvailable()).thenReturn(true);
        when(provider.getModelName()).thenReturn(MODEL);
        when(provider.embed(List.of("병원 해킹"))).thenReturn(List.<float[]>of(QUERY_VECTOR));
    }

    @Test
    void returnsSemanticResultsInSimilarityOrder() {
        when(index.findMostSimilarArticleIds(QUERY_VECTOR, 30, MINIMUM_SIMILARITY)).thenReturn(List.of(3L, 2L));
        // DB 는 ID 순으로 돌려줌: 응답은 유사도 순서 유지
        when(articleRepository.findAllById(List.of(3L, 2L))).thenReturn(List.of(firstSemanticArticle, secondSemanticArticle));

        ArticleSearchResponse response = service.search("  병원 해킹  ", null);

        assertThat(response.searchMode()).isEqualTo(ArticleSearchMode.SEMANTIC);
        assertThat(response.items()).extracting(ArticleResponse::id).containsExactly(3L, 2L);
        verify(index).refreshIfStale(MODEL);
    }

    @Test
    void fallsBackToKeywordWhenSemanticFindsNothing() {
        when(index.findMostSimilarArticleIds(any(), anyInt(), anyDouble())).thenReturn(List.of());

        ArticleSearchResponse response = service.search("병원 해킹", 10);

        assertThat(response.searchMode()).isEqualTo(ArticleSearchMode.KEYWORD);
        assertThat(response.items()).extracting(ArticleResponse::id).containsExactly(1L);
    }

    @Test
    void usesKeywordWithoutCallingProviderWhenNotAvailable() {
        when(provider.isAvailable()).thenReturn(false);

        assertThat(service.search("병원 해킹", null).searchMode()).isEqualTo(ArticleSearchMode.KEYWORD);
        verify(provider, never()).embed(any());
        verifyNoInteractions(index);
    }

    @Test
    void fallsBackToKeywordWhenProviderFails() {
        when(provider.embed(any())).thenThrow(new CustomException(ErrorCode.AI_API_ERROR, "status=503"));

        ArticleSearchResponse response = service.search("병원 해킹", null);

        assertThat(response.searchMode()).isEqualTo(ArticleSearchMode.KEYWORD);
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void fallsBackToKeywordWhenIndexRefreshFails() {
        doThrow(new IllegalStateException("db down")).when(index).refreshIfStale(MODEL);

        assertThat(service.search("병원 해킹", null).searchMode()).isEqualTo(ArticleSearchMode.KEYWORD);
    }

    @Test
    void fallsBackToKeywordWhenQueryEmbeddingExceedsWaitLimit() throws Exception {
        CountDownLatch releaseProvider = new CountDownLatch(1);
        when(provider.embed(any())).thenAnswer(invocation -> {
            releaseProvider.await(5, TimeUnit.SECONDS);
            return List.<float[]>of(QUERY_VECTOR);
        });

        ArticleSearchResponse response = service.search("병원 해킹", null);

        releaseProvider.countDown();
        assertThat(response.searchMode()).isEqualTo(ArticleSearchMode.KEYWORD);
        verifyNoInteractions(index);
    }

    @Test
    void searchesCveIdQueryByKeywordOnly() {
        assertThat(service.search("CVE-2026-5555", null).searchMode()).isEqualTo(ArticleSearchMode.KEYWORD);
        verify(provider, never()).embed(any());
    }

    @Test
    void rejectsInvalidQueryOrSizeBeforeAnyLookup() {
        assertThatThrownBy(() -> service.search(" 가 ", null)).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> service.search("병원 해킹", 51)).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(provider, never()).embed(any());
        verify(articleRepository, never()).findAllById(any());
    }

    @Test
    void passesRequestedSizeToSemanticLookup() {
        when(index.findMostSimilarArticleIds(eq(QUERY_VECTOR), eq(5), eq(MINIMUM_SIMILARITY))).thenReturn(List.of(2L));
        when(articleRepository.findAllById(List.of(2L))).thenReturn(List.of(firstSemanticArticle));

        assertThat(service.search("병원 해킹", 5).items()).extracting(ArticleResponse::id).containsExactly(2L);
    }

    private static Article createArticle(Long id, String title) {
        Article article = Article.create(
                createSource(SourceCode.DAILYSECU, ArticleKind.NEWS, ArticleRegion.DOMESTIC, FeedFormat.RSS, null),
                new ArticleDraft(ArticleKind.NEWS, ArticleRegion.DOMESTIC, title,
                        "https://www.dailysecu.com/news/articleView.html?idxno=" + id, "데일리시큐", NOW, false,
                        null, null, null, false, false),
                NOW);
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }
}
