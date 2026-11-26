package kr.suhsaechan.secbrief.application.embedding;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.embedding.TextEmbeddingProvider;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleEmbeddingRepository;
import kr.suhsaechan.secbrief.advisory.search.ArticleEmbeddingIndex;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 임베딩 묶음 처리: 제공자 미설정이면 건너뜀, 요청 묶음 크기로 나눠 임베딩·저장, 제공자 호출 실패·벡터 수 불일치는 기록 없이 멈춤
 * (가짜 제공자·저장소, 대기열 조건과 실제 저장은 DB 테스트가 확인)
 */
class ArticleEmbeddingProcessServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
    private static final String MODEL = "test-embedding-model";
    private static final int BATCH_SIZE = 5;
    private static final int REQUEST_BATCH_SIZE = 2;

    private final TextEmbeddingProvider provider = mock(TextEmbeddingProvider.class);
    private final ArticleEmbeddingRepository repository = mock(ArticleEmbeddingRepository.class);
    private final ArticleEmbeddingResultRecorder recorder = mock(ArticleEmbeddingResultRecorder.class);
    private final ArticleEmbeddingProcessService service = new ArticleEmbeddingProcessService(
            provider, repository, recorder, Clock.fixed(NOW, ZoneOffset.UTC), BATCH_SIZE, REQUEST_BATCH_SIZE);

    @BeforeEach
    void givenAvailableProviderReturningOneVectorPerText() {
        when(provider.isAvailable()).thenReturn(true);
        when(provider.getModelName()).thenReturn(MODEL);
        when(provider.embed(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            List<float[]> embeddings = new ArrayList<>();
            texts.forEach(text -> embeddings.add(new float[] {text.length(), 1f}));
            return embeddings;
        });
    }

    @Test
    void skipsWhenProviderNotConfigured() {
        when(provider.isAvailable()).thenReturn(false);

        assertThat(service.processMissingEmbeddings()).isEqualTo(new EmbeddingProcessResult(0, true));
        verifyNoInteractions(repository, recorder);
        verify(provider, never()).embed(any());
    }

    @Test
    void asksTargetsOfRecentArticlesWaitingForAiUpToBatchSize() {
        givenTargets(0);

        assertThat(service.processMissingEmbeddings()).isEqualTo(new EmbeddingProcessResult(0, false));
        verify(repository).findEmbeddingTargets(MODEL, NOW.minus(ArticleEmbeddingIndex.SEARCHABLE_PUBLISHED_WITHIN),
                NOW.minus(ArticleEmbeddingProcessService.AI_WAIT_DURATION), Limit.of(BATCH_SIZE));
        verify(provider, never()).embed(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void embedsInRequestBatchesAndRecordsVectorsByArticleId() {
        givenTargets(5);

        assertThat(service.processMissingEmbeddings()).isEqualTo(new EmbeddingProcessResult(5, false));

        // 5건 > 2 + 2 + 1
        ArgumentCaptor<List<String>> textsCaptor = ArgumentCaptor.forClass(List.class);
        verify(provider, times(3)).embed(textsCaptor.capture());
        assertThat(textsCaptor.getAllValues()).extracting(List::size).containsExactly(2, 2, 1);
        assertThat(textsCaptor.getAllValues().get(0)).containsExactly("기사 제목 1", "기사 제목 2");
        ArgumentCaptor<Map<Long, float[]>> embeddingsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(recorder, times(3)).recordEmbeddings(eq(MODEL), embeddingsCaptor.capture());
        assertThat(embeddingsCaptor.getAllValues().get(0).keySet()).containsExactly(1L, 2L);
        assertThat(embeddingsCaptor.getAllValues().get(2).keySet()).containsExactly(5L);
        assertThat(embeddingsCaptor.getAllValues().get(0).get(1L)).containsExactly("기사 제목 1".length(), 1f);
    }

    @Test
    void stopsTickWithoutRecordingWhenProviderCallFails() {
        givenTargets(5);
        when(provider.embed(anyList())).thenThrow(new CustomException(ErrorCode.AI_API_ERROR, "status=503"));

        assertThat(service.processMissingEmbeddings()).isEqualTo(new EmbeddingProcessResult(0, false));
        // 첫 묶음 실패 뒤 남은 묶음은 호출하지 않음
        verify(provider, times(1)).embed(anyList());
        verifyNoInteractions(recorder);
    }

    @Test
    void keepsEarlierBatchesWhenLaterProviderCallFails() {
        givenTargets(5);
        when(provider.embed(anyList()))
                .thenReturn(List.of(new float[] {1f}, new float[] {2f}))
                .thenThrow(new IllegalStateException("boom"));

        assertThat(service.processMissingEmbeddings()).isEqualTo(new EmbeddingProcessResult(2, false));
        verify(recorder, times(1)).recordEmbeddings(eq(MODEL), any());
    }

    @Test
    void stopsWithoutRecordingWhenEmbeddingCountDiffersFromArticles() {
        givenTargets(2);
        when(provider.embed(anyList())).thenReturn(List.<float[]>of(new float[] {1f}));

        assertThat(service.processMissingEmbeddings()).isEqualTo(new EmbeddingProcessResult(0, false));
        verifyNoInteractions(recorder);
    }

    @Test
    void stopsTickWhenRecordingFails() {
        givenTargets(5);
        doThrow(new IllegalStateException("db down")).when(recorder).recordEmbeddings(any(), any());

        assertThat(service.processMissingEmbeddings()).isEqualTo(new EmbeddingProcessResult(0, false));
        verify(provider, times(1)).embed(anyList());
    }

    private void givenTargets(int articleCount) {
        List<Article> targetArticles = IntStream.rangeClosed(1, articleCount)
                .mapToObj(ArticleEmbeddingProcessServiceTest::createArticle)
                .toList();
        when(repository.findEmbeddingTargets(any(), any(), any(), any())).thenReturn(targetArticles);
    }

    private static Article createArticle(int number) {
        Article article = Article.create(
                createSource(SourceCode.DAILYSECU, ArticleKind.NEWS, ArticleRegion.DOMESTIC, FeedFormat.RSS, null),
                new ArticleDraft(ArticleKind.NEWS, ArticleRegion.DOMESTIC, "기사 제목 " + number,
                        "https://www.dailysecu.com/news/articleView.html?idxno=" + number, "데일리시큐", NOW, false,
                        null, null, null, false, false),
                NOW);
        ReflectionTestUtils.setField(article, "id", (long) number);
        return article;
    }
}
