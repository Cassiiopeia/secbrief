package kr.suhsaechan.secbrief.advisory.collector;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * GitHub 보안 권고 수집기: 최신 게시순 1페이지(100건) > since 이후분
 * 인증 없음 (시간당 60회 한도, 출처 주기 2시간이라 1회/2시간)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GhsaArticleCollector implements ArticleCollector {

    // 한 페이지 최대 건수 (주간 critical 권고가 이보다 적다는 가정, 꽉 차면 로그)
    public static final int RESULTS_PER_PAGE = 100;
    // feedUrl 에 이미 쿼리(type, severity)가 있어 & 로 이음
    private static final String PAGE_QUERY = "&per_page=" + RESULTS_PER_PAGE + "&sort=published&direction=desc";
    private static final Map<String, String> GITHUB_API_HEADERS = Map.of(
            "Accept", "application/vnd.github+json",
            "X-GitHub-Api-Version", "2022-11-28");

    private final FeedHttpClient feedHttpClient;

    @Override
    public FeedFormat supportedFeedFormat() {
        return FeedFormat.GHSA_API;
    }

    @Override
    public List<CollectedArticle> collect(Source source, Instant since) {
        byte[] responseBytes = feedHttpClient.fetch(source.getFeedUrl() + PAGE_QUERY, GITHUB_API_HEADERS);
        List<CollectedArticle> allArticles = GhsaAdvisoryParser.parse(new ByteArrayInputStream(responseBytes), null);
        if (allArticles.size() >= RESULTS_PER_PAGE) {
            log.warn("[article collect] ghsa page full : size={}", allArticles.size());
        }
        return allArticles.stream()
                .filter(article -> since == null || !article.publishedAt().isBefore(since))
                .toList();
    }
}
