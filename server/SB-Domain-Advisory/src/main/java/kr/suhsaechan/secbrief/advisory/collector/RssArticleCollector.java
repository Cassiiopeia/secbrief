package kr.suhsaechan.secbrief.advisory.collector;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * RSS 출처 수집기: 피드 내려받기 > 파싱, 게시자는 출처 표시 이름
 * since 는 쓰지 않음 (피드가 주는 만큼 전부, 중복은 저장 단계가 거름)
 */
@Component
@RequiredArgsConstructor
public class RssArticleCollector implements ArticleCollector {

    private final FeedHttpClient feedHttpClient;

    @Override
    public FeedFormat supportedFeedFormat() {
        return FeedFormat.RSS;
    }

    @Override
    public List<CollectedArticle> collect(Source source, Instant since) {
        byte[] feedBytes = feedHttpClient.fetch(source.getFeedUrl());
        return RssFeedParser.parse(new ByteArrayInputStream(feedBytes), source.getDisplayName());
    }
}
