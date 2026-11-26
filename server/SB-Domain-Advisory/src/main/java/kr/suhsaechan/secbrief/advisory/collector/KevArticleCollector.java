package kr.suhsaechan.secbrief.advisory.collector;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * CISA KEV 수집기: 카탈로그 JSON 조건부 내려받기 > 변경 없으면 종료 > 파싱, since 이후 등재분만
 */
@Component
@RequiredArgsConstructor
public class KevArticleCollector implements ArticleCollector {

    private final FeedHttpClient feedHttpClient;

    @Override
    public FeedFormat supportedFeedFormat() {
        return FeedFormat.CISA_KEV_JSON;
    }

    @Override
    public CollectOutcome collect(Source source, Instant since) {
        FeedFetchResult fetchResult = feedHttpClient.fetchIfChanged(source.getFeedUrl(), FeedValidators.of(source));
        if (fetchResult.isNotModified()) {
            return CollectOutcome.notModified(fetchResult.validators());
        }
        return CollectOutcome.ofArticles(
                KevCatalogParser.parse(new ByteArrayInputStream(fetchResult.body()), since),
                fetchResult.validators());
    }
}
