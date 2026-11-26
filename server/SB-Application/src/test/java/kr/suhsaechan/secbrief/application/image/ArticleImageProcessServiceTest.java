package kr.suhsaechan.secbrief.application.image;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.ImageStatus;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.image.ArticleImageDownloader;
import kr.suhsaechan.secbrief.advisory.image.ArticleImageResizer;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 이미지 묶음 처리: 저장소 미설정이면 건너뜀, 건별 저장·실패 기록, 저장소 장애·시간 상한이면 기록 없이 멈춤
 * (가짜 저장소·가짜 내려받기, 3회 실패 > FAILED 는 DB 테스트 ArticleImageProcessTest 가 확인)
 */
class ArticleImageProcessServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
    private static final int BATCH_SIZE = 20;
    private static final long TICK_BUDGET_SECONDS = 60;
    private static final byte[] ORIGINAL_BYTES = {1, 2, 3};
    private static final byte[] RESIZED_BYTES = {9, 8};

    private final FakeArticleImageStorage fakeStorage = new FakeArticleImageStorage();
    private final ArticleImageDownloader downloader = mock(ArticleImageDownloader.class);
    private final ArticleImageResizer resizer = mock(ArticleImageResizer.class);
    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final ArticleImageResultRecorder recorder = mock(ArticleImageResultRecorder.class);
    private final AtomicReference<Instant> currentTime = new AtomicReference<>(NOW);
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentTime.get();
        }
    };
    private final ArticleImageProcessService service = new ArticleImageProcessService(
            fakeStorage, downloader, resizer, articleRepository, recorder, clock, BATCH_SIZE, TICK_BUDGET_SECONDS);

    @Test
    void skipsWhenStorageNotConfigured() {
        fakeStorage.setAvailable(false);

        assertThat(service.processPendingImages()).isEqualTo(new ImageProcessResult(0, 0, true));
        verifyNoInteractions(articleRepository, downloader);
    }

    @Test
    void storesResizedJpegAndRecordsObjectKey() {
        givenPendingArticles(createArticle(7L));
        when(downloader.download(toImageUrl(7L))).thenReturn(ORIGINAL_BYTES);
        when(resizer.toResizedJpeg(ORIGINAL_BYTES)).thenReturn(RESIZED_BYTES);

        ImageProcessResult result = service.processPendingImages();

        assertThat(result).isEqualTo(new ImageProcessResult(1, 0, false));
        assertThat(fakeStorage.getStoredBytesByObjectKey()).containsOnlyKeys("articles/2026/11/7.jpg");
        assertThat(fakeStorage.getStoredBytesByObjectKey().get("articles/2026/11/7.jpg")).isEqualTo(RESIZED_BYTES);
        verify(recorder).recordStored(7L, "articles/2026/11/7.jpg");
    }

    @Test
    void downloadFailureFailsOnlyThatArticle() {
        givenPendingArticles(createArticle(2L), createArticle(1L));
        when(downloader.download(toImageUrl(2L)))
                .thenThrow(new CustomException(ErrorCode.EXTERNAL_API_ERROR, "blocked address"));
        when(downloader.download(toImageUrl(1L))).thenReturn(ORIGINAL_BYTES);
        when(resizer.toResizedJpeg(ORIGINAL_BYTES)).thenReturn(RESIZED_BYTES);

        ImageProcessResult result = service.processPendingImages();

        assertThat(result).isEqualTo(new ImageProcessResult(1, 1, false));
        verify(recorder).recordFailure(2L);
        verify(recorder).recordStored(eq(1L), anyString());
        assertThat(fakeStorage.getStoredBytesByObjectKey()).containsOnlyKeys("articles/2026/11/1.jpg");
    }

    @Test
    void resizeFailureRecordsFailureWithoutStoring() {
        givenPendingArticles(createArticle(1L));
        when(downloader.download(anyString())).thenReturn(ORIGINAL_BYTES);
        when(resizer.toResizedJpeg(ORIGINAL_BYTES))
                .thenThrow(new CustomException(ErrorCode.EXTERNAL_API_ERROR, "unsupported image format"));

        assertThat(service.processPendingImages()).isEqualTo(new ImageProcessResult(0, 1, false));
        verify(recorder).recordFailure(1L);
        assertThat(fakeStorage.getStoredBytesByObjectKey()).isEmpty();
    }

    @Test
    void storageFailureStopsTickWithoutRecordingFailure() {
        fakeStorage.setStoreFailing(true);
        givenPendingArticles(createArticle(2L), createArticle(1L));
        when(downloader.download(anyString())).thenReturn(ORIGINAL_BYTES);
        when(resizer.toResizedJpeg(ORIGINAL_BYTES)).thenReturn(RESIZED_BYTES);

        ImageProcessResult result = service.processPendingImages();

        // 기사 탓이 아니라 시도 횟수를 올리지 않고, 남은 건도 내려받지 않음
        assertThat(result).isEqualTo(new ImageProcessResult(0, 0, false));
        verifyNoInteractions(recorder);
        verify(downloader, never()).download(toImageUrl(1L));
    }

    @Test
    void countsAsFailedWithoutFailureRecordWhenStoredRecordThrows() {
        givenPendingArticles(createArticle(1L));
        when(downloader.download(anyString())).thenReturn(ORIGINAL_BYTES);
        when(resizer.toResizedJpeg(ORIGINAL_BYTES)).thenReturn(RESIZED_BYTES);
        doThrow(new IllegalStateException("db")).when(recorder).recordStored(eq(1L), anyString());

        // PENDING 으로 남아 다음 틱이 같은 키에 다시 저장
        assertThat(service.processPendingImages()).isEqualTo(new ImageProcessResult(0, 1, false));
        verify(recorder, never()).recordFailure(1L);
    }

    @Test
    void stopsStartingNewArticlesAfterTickBudget() {
        givenPendingArticles(createArticle(2L), createArticle(1L));
        when(downloader.download(toImageUrl(2L))).thenAnswer(invocation -> {
            // 첫 건이 시간 상한을 다 씀
            currentTime.set(NOW.plus(Duration.ofSeconds(TICK_BUDGET_SECONDS + 1)));
            return ORIGINAL_BYTES;
        });
        when(resizer.toResizedJpeg(ORIGINAL_BYTES)).thenReturn(RESIZED_BYTES);

        assertThat(service.processPendingImages()).isEqualTo(new ImageProcessResult(1, 0, false));
        verify(downloader, never()).download(toImageUrl(1L));
    }

    @Test
    void readsAtMostBatchSizeNewestFirst() {
        givenPendingArticles();

        service.processPendingImages();

        verify(articleRepository).findByImageState_StatusOrderByIdDesc(ImageStatus.PENDING, Limit.of(BATCH_SIZE));
        verifyNoInteractions(downloader);
    }

    private void givenPendingArticles(Article... articles) {
        when(articleRepository.findByImageState_StatusOrderByIdDesc(eq(ImageStatus.PENDING), any())).thenReturn(List.of(articles));
    }

    private static Article createArticle(Long id) {
        Article article = Article.create(
                createSource(SourceCode.THE_HACKER_NEWS, ArticleKind.NEWS, ArticleRegion.OVERSEAS, FeedFormat.RSS, null),
                new ArticleDraft(ArticleKind.NEWS, ArticleRegion.OVERSEAS, "Headline " + id, "https://thehackernews.com/" + id,
                        "The Hacker News", NOW, false, null, null, null, false, false, toImageUrl(id)),
                NOW);
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }

    private static String toImageUrl(Long articleId) {
        return "https://images.example.test/" + articleId + ".jpg";
    }
}
