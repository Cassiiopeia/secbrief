package kr.suhsaechan.secbrief.advisory.collector;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.CollectSchedulePolicy;
import org.springframework.stereotype.Component;

/**
 * NVD 수집기: 게시일 범위(since ~ 지금)로 페이지를 끝까지 요청 > CVSS 9.0 이상만
 * 요청마다 6초 간격 (NvdRequestThrottle)
 */
@Component
public class NvdArticleCollector implements ArticleCollector {

    // NVD 한 페이지 최대 건수
    public static final int RESULTS_PER_PAGE = 2000;
    // NVD 날짜 형식 (오프셋 없음 = UTC). 콜론은 쿼리에서 그대로 허용되어 인코딩 불필요
    private static final DateTimeFormatter NVD_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    private final FeedHttpClient feedHttpClient;
    private final Clock clock;
    private final NvdRequestThrottle requestThrottle;

    public NvdArticleCollector(FeedHttpClient feedHttpClient, Clock clock) {
        this.feedHttpClient = feedHttpClient;
        this.clock = clock;
        this.requestThrottle = new NvdRequestThrottle(clock, Thread::sleep);
    }

    @Override
    public FeedFormat supportedFeedFormat() {
        return FeedFormat.NVD_API;
    }

    /**
     * since 가 null 이면 첫 수집 범위(7일)로 대체 (범위 없이 요청하면 NVD 전체를 받게 됨)
     */
    @Override
    public List<CollectedArticle> collect(Source source, Instant since) {
        Instant now = clock.instant();
        Instant rangeStart = since != null ? since : now.minus(Duration.ofDays(CollectSchedulePolicy.NVD_FIRST_COLLECT_DAYS));
        List<CollectedArticle> articles = new ArrayList<>();
        int startIndex = 0;
        NvdPage page;
        do {
            requestThrottle.awaitTurn();
            byte[] responseBytes = feedHttpClient.fetch(toRequestUrl(source.getFeedUrl(), rangeStart, now, startIndex));
            page = NvdResponseParser.parse(new ByteArrayInputStream(responseBytes));
            articles.addAll(page.articles());
            startIndex = page.startIndex() + page.resultsPerPage();
        } while (page.hasNextPage());
        return articles;
    }

    /**
     * 요청 주소: {feedUrl}?pubStartDate=..&pubEndDate=..&resultsPerPage=2000&startIndex=n
     */
    String toRequestUrl(String feedUrl, Instant since, Instant now, int startIndex) {
        return new StringBuilder(feedUrl)
                .append("?pubStartDate=").append(NVD_DATE_FORMATTER.format(since))
                .append("&pubEndDate=").append(NVD_DATE_FORMATTER.format(now))
                .append("&resultsPerPage=").append(RESULTS_PER_PAGE)
                .append("&startIndex=").append(startIndex)
                .toString();
    }
}
