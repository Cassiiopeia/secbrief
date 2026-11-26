package kr.suhsaechan.secbrief.advisory.collector;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * KISA 게시판 쪽 넘김: 1쪽부터 차례로 읽기 > 범위 안 글 모으기 > 범위 밖·마지막 쪽·쪽 수 상한에서 멈춤
 * 쪽 사이 2초 쉼 (출처 부담), 게시판은 캐시 검증값(ETag·Last-Modified)을 주지 않아 일반 GET
 */
@Component
public class KisaBoardPageWalker {

    // 쪽 요청 간격 (2초)
    static final Duration PAGE_REQUEST_INTERVAL = Duration.ofSeconds(2);
    // 한 번에 읽는 쪽 수 상한 (보안공지 1년치 약 100쪽, 쪽당 일반 글 10건)
    static final int MAXIMUM_PAGE_COUNT = 200;

    private final FeedHttpClient feedHttpClient;
    private final Duration pageRequestInterval;

    @Autowired
    public KisaBoardPageWalker(FeedHttpClient feedHttpClient) {
        this(feedHttpClient, PAGE_REQUEST_INTERVAL);
    }

    // 쉼 없이 확인하는 테스트용
    KisaBoardPageWalker(FeedHttpClient feedHttpClient, Duration pageRequestInterval) {
        this.feedHttpClient = feedHttpClient;
        this.pageRequestInterval = pageRequestInterval;
    }

    /**
     * earliestPublishedAt 이후 글 모으기 (null 이면 게시판 전체), 게시자는 출처 표시 이름
     * 일반 글은 최신순이라 범위보다 오래된 글이 나온 쪽에서 멈춤, 고정 글은 범위 안인 것만
     */
    public KisaBoardWalkResult collectSince(Source source, KisaBoard board, Instant earliestPublishedAt) {
        Map<String, CollectedArticle> articleByUrl = new LinkedHashMap<>();
        int pageIndex = 1;
        while (true) {
            KisaBoardPage page = KisaBoardListParser.parse(
                    feedHttpClient.fetch(board.toListPageUrl(pageIndex)), board, source.getDisplayName(), pageIndex);
            addWithinRange(page.pinnedArticles(), earliestPublishedAt, articleByUrl);
            addWithinRange(page.regularArticles(), earliestPublishedAt, articleByUrl);

            boolean isPageLimitReached = pageIndex >= MAXIMUM_PAGE_COUNT;
            if (!page.hasNextPage() || hasReachedRangeEnd(page.regularArticles(), earliestPublishedAt) || isPageLimitReached) {
                return new KisaBoardWalkResult(List.copyOf(articleByUrl.values()), pageIndex,
                        isPageLimitReached && page.hasNextPage());
            }
            pauseBetweenPages();
            pageIndex++;
        }
    }

    private static void addWithinRange(List<CollectedArticle> articles, Instant earliestPublishedAt,
                                       Map<String, CollectedArticle> articleByUrl) {
        articles.stream()
                .filter(article -> isWithinRange(article, earliestPublishedAt))
                .forEach(article -> articleByUrl.putIfAbsent(article.originalUrl(), article));
    }

    // 빈 쪽이거나 쪽의 마지막(가장 오래된) 일반 글이 범위보다 오래됨
    private static boolean hasReachedRangeEnd(List<CollectedArticle> regularArticles, Instant earliestPublishedAt) {
        return regularArticles.isEmpty() || !isWithinRange(regularArticles.getLast(), earliestPublishedAt);
    }

    private static boolean isWithinRange(CollectedArticle article, Instant earliestPublishedAt) {
        return earliestPublishedAt == null || !article.publishedAt().isBefore(earliestPublishedAt);
    }

    // 서버 종료 등으로 끊기면 실패로 던짐 (채우기 끝 표시를 남기지 않아 다음 기동에서 다시 함)
    private void pauseBetweenPages() {
        if (pageRequestInterval.isZero()) {
            return;
        }
        try {
            Thread.sleep(pageRequestInterval);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR, "interrupted");
        }
    }
}
