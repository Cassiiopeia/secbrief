package kr.suhsaechan.secbrief.advisory.collector;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * CISA KEV 수집기: 카탈로그 JSON 내려받기 > 파싱, since 이후 등재분만
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
    public List<CollectedArticle> collect(Source source, Instant since) {
        byte[] catalogBytes = feedHttpClient.fetch(source.getFeedUrl());
        return KevCatalogParser.parse(new ByteArrayInputStream(catalogBytes), since);
    }
}
