package kr.suhsaechan.secbrief.web.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ImageStatus;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.image.ArticleImageDownloader;
import kr.suhsaechan.secbrief.advisory.image.ArticleImageStorage;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.advisory.service.ArticleImageQueryService;
import kr.suhsaechan.secbrief.advisory.service.ArticleSaveService;
import kr.suhsaechan.secbrief.application.image.ArticleImageProcessService;
import kr.suhsaechan.secbrief.application.image.ImageProcessResult;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 기사 대표 이미지 (DB): 저장 시 대기열 등록, 대기열 조회(최신부터·건수 제한), 처리 > STORED + 응답 경로 + 읽기, 3회 실패 > FAILED
 * 저장소와 내려받기만 가짜 (외부 호출 없음), 줄이기는 실제 구현
 */
@SpringBootTest
@ActiveProfiles("test")
class ArticleImageProcessTest extends PostgresContainerTestSupport {

    // 2026-11-23 09:00 서울
    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
    private static final byte[] STORED_JPEG_BYTES = {(byte) 0xff, (byte) 0xd8, 1, 2};

    @Autowired
    private ArticleImageProcessService articleImageProcessService;
    @Autowired
    private ArticleImageQueryService articleImageQueryService;
    @Autowired
    private ArticleSaveService articleSaveService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private SourceRepository sourceRepository;
    @MockitoBean
    private ArticleImageStorage articleImageStorage;
    @MockitoBean
    private ArticleImageDownloader articleImageDownloader;

    private Source source;

    @BeforeEach
    void cleanArticles() {
        articleRepository.deleteAll();
        source = sourceRepository.findByCode(SourceCode.THE_HACKER_NEWS).orElseThrow();
        given(articleImageStorage.isAvailable()).willReturn(true);
    }

    @Test
    void savedArticleWaitsInQueueOnlyWhenFeedGaveImage() {
        Long articleWithImageId = saveArticle("with-image", "https://images.example.test/cover.jpg");
        Long articleWithoutImageId = saveArticle("without-image", null);

        assertThat(articleRepository.findById(articleWithoutImageId).orElseThrow().getImageState().getStatus()).isEqualTo(ImageStatus.NONE);
        assertThat(articleRepository.findByImageState_StatusOrderByIdDesc(ImageStatus.PENDING, Limit.of(20)))
                .extracting(Article::getId).containsExactly(articleWithImageId);
    }

    @Test
    void readsQueueNewestFirstWithinLimit() {
        saveArticle("first", "https://images.example.test/1.jpg");
        Long secondArticleId = saveArticle("second", "https://images.example.test/2.jpg");
        Long thirdArticleId = saveArticle("third", "https://images.example.test/3.jpg");

        assertThat(articleRepository.findByImageState_StatusOrderByIdDesc(ImageStatus.PENDING, Limit.of(2)))
                .extracting(Article::getId).containsExactly(thirdArticleId, secondArticleId);
    }

    @Test
    void storesResizedJpegAndExposesImagePath() throws IOException {
        Long articleId = saveArticle("stored", "https://images.example.test/cover.png");
        given(articleImageDownloader.download("https://images.example.test/cover.png")).willReturn(createPngBytes(1600, 800));
        String expectedObjectKey = "articles/2026/11/" + articleId + ".jpg";

        ImageProcessResult result = articleImageProcessService.processPendingImages();

        assertThat(result).isEqualTo(new ImageProcessResult(1, 0, false));
        ArgumentCaptor<byte[]> storedBytes = ArgumentCaptor.forClass(byte[].class);
        verify(articleImageStorage).store(eq(expectedObjectKey), storedBytes.capture());
        BufferedImage storedImage = ImageIO.read(new ByteArrayInputStream(storedBytes.getValue()));
        assertThat(storedImage.getWidth()).isEqualTo(800);
        assertThat(storedImage.getHeight()).isEqualTo(400);

        Article article = articleRepository.findById(articleId).orElseThrow();
        assertThat(article.getImageState().getStatus()).isEqualTo(ImageStatus.STORED);
        assertThat(article.getImageState().getObjectKey()).isEqualTo(expectedObjectKey);
        assertThat(ArticleResponse.from(article).imagePath()).isEqualTo("/api/v1/articles/" + articleId + "/image");
        assertThat(articleRepository.findByImageState_StatusOrderByIdDesc(ImageStatus.PENDING, Limit.of(20))).isEmpty();

        given(articleImageStorage.read(expectedObjectKey)).willReturn(Optional.of(STORED_JPEG_BYTES));
        assertThat(articleImageQueryService.findStoredImage(articleId)).isEqualTo(STORED_JPEG_BYTES);
    }

    @Test
    void marksFailedAfterThreeTicksAndStopsRetrying() {
        Long articleId = saveArticle("failing", "https://images.example.test/broken.jpg");
        given(articleImageDownloader.download(anyString()))
                .willThrow(new CustomException(ErrorCode.EXTERNAL_API_ERROR, "status=404"));

        List<ImageProcessResult> results = List.of(
                articleImageProcessService.processPendingImages(),
                articleImageProcessService.processPendingImages(),
                articleImageProcessService.processPendingImages(),
                articleImageProcessService.processPendingImages());

        assertThat(results).containsExactly(new ImageProcessResult(0, 1, false), new ImageProcessResult(0, 1, false),
                new ImageProcessResult(0, 1, false), new ImageProcessResult(0, 0, false));
        Article article = articleRepository.findById(articleId).orElseThrow();
        assertThat(article.getImageState().getStatus()).isEqualTo(ImageStatus.FAILED);
        assertThat(article.getImageState().getAttemptCount()).isEqualTo(3);
        assertThat(ArticleResponse.from(article).imagePath()).isNull();
        // 네 번째 틱은 내려받지 않음
        verify(articleImageDownloader, times(3)).download(anyString());
    }

    @Test
    void storageFailureKeepsArticlePendingWithoutAttempt() throws IOException {
        Long articleId = saveArticle("storage-down", "https://images.example.test/cover.png");
        given(articleImageDownloader.download(anyString())).willReturn(createPngBytes(400, 300));
        org.mockito.BDDMockito.willThrow(new CustomException(ErrorCode.IMAGE_STORAGE_ERROR, "store failed : AccessDenied"))
                .given(articleImageStorage).store(anyString(), any());

        articleImageProcessService.processPendingImages();

        Article article = articleRepository.findById(articleId).orElseThrow();
        assertThat(article.getImageState().getStatus()).isEqualTo(ImageStatus.PENDING);
        assertThat(article.getImageState().getAttemptCount()).isZero();
    }

    @Test
    void imageIsNotFoundBeforeStoredOrWhenObjectIsMissing() throws IOException {
        Long pendingArticleId = saveArticle("pending", "https://images.example.test/cover.png");
        assertNotFound(pendingArticleId);
        assertNotFound(Long.MAX_VALUE);

        // 기록은 STORED 인데 저장소에 객체가 없는 경우
        given(articleImageDownloader.download(anyString())).willReturn(createPngBytes(400, 300));
        articleImageProcessService.processPendingImages();
        given(articleImageStorage.read(anyString())).willReturn(Optional.empty());
        assertNotFound(pendingArticleId);
    }

    @Test
    void storedImageWithoutStorageSettingIsStorageError() throws IOException {
        Long articleId = saveArticle("unconfigured", "https://images.example.test/cover.png");
        given(articleImageDownloader.download(anyString())).willReturn(createPngBytes(400, 300));
        articleImageProcessService.processPendingImages();
        given(articleImageStorage.isAvailable()).willReturn(false);

        assertThatThrownBy(() -> articleImageQueryService.findStoredImage(articleId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IMAGE_STORAGE_ERROR);
    }

    private void assertNotFound(Long articleId) {
        assertThatThrownBy(() -> articleImageQueryService.findStoredImage(articleId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    // 수집 저장 경로 그대로 저장 (초안의 이미지 주소가 열에 실리는지까지 확인)
    private Long saveArticle(String slug, String imageSourceUrl) {
        String originalUrl = "https://thehackernews.com/2026/11/" + slug + ".html";
        articleSaveService.saveOrMerge(source, new ArticleDraft(ArticleKind.NEWS, ArticleRegion.OVERSEAS, "Headline " + slug,
                originalUrl, "The Hacker News", NOW, false, null, null, null, false, false, imageSourceUrl), NOW);
        return articleRepository.findAll().stream()
                .filter(article -> article.getOriginalUrl().equals(originalUrl))
                .findFirst().orElseThrow().getId();
    }

    private static byte[] createPngBytes(int width, int height) throws IOException {
        ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", pngBytes);
        return pngBytes.toByteArray();
    }
}
