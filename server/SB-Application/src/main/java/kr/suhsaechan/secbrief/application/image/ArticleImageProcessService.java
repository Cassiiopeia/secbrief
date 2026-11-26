package kr.suhsaechan.secbrief.application.image;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ImageStatus;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.image.ArticleImageDownloader;
import kr.suhsaechan.secbrief.advisory.image.ArticleImageObjectKey;
import kr.suhsaechan.secbrief.advisory.image.ArticleImageResizer;
import kr.suhsaechan.secbrief.advisory.image.ArticleImageStorage;
import kr.suhsaechan.secbrief.advisory.policy.ArticleNormalizer;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 이미지 묶음 처리: 저장소 확인 > 대기(PENDING) 기사 batchSize 건 > 건별 내려받기 > 줄이기 > 저장 > 결과 기록
 * 건별 격리 (한 건 실패가 다음 건을 막지 않음), 저장소 장애는 기록 없이 틱 중단, 이미지 주소는 로그에 남기지 않음
 */
@Slf4j
@Service
public class ArticleImageProcessService {

    private final ArticleImageStorage articleImageStorage;
    private final ArticleImageDownloader articleImageDownloader;
    private final ArticleImageResizer articleImageResizer;
    private final ArticleRepository articleRepository;
    private final ArticleImageResultRecorder articleImageResultRecorder;
    private final Clock clock;
    // 한 틱에 처리할 기사 수
    private final int batchSize;
    // 한 틱이 새 건을 시작할 수 있는 시간: 주기 작업이 스레드 하나를 나눠 써서 느린 출처가 수집·알림을 밀지 않게 함
    private final Duration tickBudget;

    public ArticleImageProcessService(ArticleImageStorage articleImageStorage, ArticleImageDownloader articleImageDownloader,
                                      ArticleImageResizer articleImageResizer, ArticleRepository articleRepository,
                                      ArticleImageResultRecorder articleImageResultRecorder, Clock clock,
                                      @Value("${secbrief.image.batch-size:20}") int batchSize,
                                      @Value("${secbrief.image.tick-budget-seconds:60}") long tickBudgetSeconds) {
        this.articleImageStorage = articleImageStorage;
        this.articleImageDownloader = articleImageDownloader;
        this.articleImageResizer = articleImageResizer;
        this.articleRepository = articleRepository;
        this.articleImageResultRecorder = articleImageResultRecorder;
        this.clock = clock;
        this.batchSize = batchSize;
        this.tickBudget = Duration.ofSeconds(tickBudgetSeconds);
    }

    public ImageProcessResult processPendingImages() {
        if (!articleImageStorage.isAvailable()) {
            log.info("[article image] skipped : storage not configured");
            return new ImageProcessResult(0, 0, true);
        }

        List<Article> pendingArticles =
                articleRepository.findByImageState_StatusOrderByIdDesc(ImageStatus.PENDING, Limit.of(batchSize));
        Instant tickDeadline = clock.instant().plus(tickBudget);
        int storedCount = 0;
        int failedCount = 0;
        for (Article article : pendingArticles) {
            // 시간 상한을 넘기면 남은 건은 다음 틱에
            if (clock.instant().isAfter(tickDeadline)) {
                break;
            }
            ImageOutcome outcome = processArticle(article);
            if (outcome == ImageOutcome.STORAGE_FAILED) {
                break;
            }
            if (outcome == ImageOutcome.STORED) {
                storedCount++;
            } else {
                failedCount++;
            }
        }
        if (!pendingArticles.isEmpty()) {
            log.info("[article image] processed : stored={}, failed={}, pending={}",
                    storedCount, failedCount, pendingArticles.size() - storedCount - failedCount);
        }
        return new ImageProcessResult(storedCount, failedCount, false);
    }

    /**
     * 기사 1건: 내려받기 > 줄이기 > 저장 > 기록 (내려받기·줄이기 실패는 실패 기록, 저장 실패는 기록 없이 틱 중단)
     */
    private ImageOutcome processArticle(Article article) {
        byte[] resizedJpegBytes;
        try {
            resizedJpegBytes = articleImageResizer.toResizedJpeg(
                    articleImageDownloader.download(article.getImageState().getSourceUrl()));
        } catch (RuntimeException exception) {
            log.warn("[article image] failed : articleId={}, cause={}", article.getId(), toLogCause(exception));
            recordFailure(article.getId());
            return ImageOutcome.FAILED;
        }

        String imageObjectKey = ArticleImageObjectKey.toObjectKey(article.getId(), article.getCollectedAt());
        try {
            articleImageStorage.store(imageObjectKey, resizedJpegBytes);
        } catch (RuntimeException exception) {
            log.error("[article image] failed 502 : storage store failed, retry next tick : cause={}", toLogCause(exception));
            return ImageOutcome.STORAGE_FAILED;
        }

        try {
            articleImageResultRecorder.recordStored(article.getId(), imageObjectKey);
            return ImageOutcome.STORED;
        } catch (RuntimeException exception) {
            // 객체는 올라갔지만 기록 실패: PENDING 으로 남아 다음 틱이 같은 키에 덮어씀
            log.error("[article image] stored record failed : articleId={}", article.getId());
            return ImageOutcome.FAILED;
        }
    }

    // 실패 기록마저 실패하면 다음 틱에 다시
    private void recordFailure(Long articleId) {
        try {
            articleImageResultRecorder.recordFailure(articleId);
        } catch (RuntimeException recordException) {
            log.error("[article image] failure record failed : articleId={}", articleId);
        }
    }

    // 업무 예외는 원인 문구(영어 고정 문구), 그 밖은 예외 종류만 (외부 값이 섞일 수 있어 제어문자 정리)
    private static String toLogCause(RuntimeException exception) {
        if (exception instanceof CustomException customException && customException.getDetail() != null) {
            return ArticleNormalizer.toSanitizedErrorMessage(customException.getDetail());
        }
        return exception.getClass().getSimpleName();
    }

    private enum ImageOutcome {
        STORED,
        FAILED,
        // 저장소 장애 (기사 탓 아님): 기록 없이 틱 중단 (FAILED 로 굳지 않게 다음 틱에 다시)
        STORAGE_FAILED
    }
}
