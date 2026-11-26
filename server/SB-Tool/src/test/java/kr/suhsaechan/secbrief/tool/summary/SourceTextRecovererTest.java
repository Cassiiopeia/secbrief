package kr.suhsaechan.secbrief.tool.summary;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.collector.FeedFetchResult;
import kr.suhsaechan.secbrief.advisory.collector.FeedHttpClient;
import kr.suhsaechan.secbrief.advisory.collector.FeedValidators;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 요약 입력 되찾기: 운영 수집기·정규화로 다시 읽어 CVE ID·원문 주소로 맞춤, 출처가 더는 주지 않는 항목은 그대로 둠
 * 출처 호출은 정해 둔 응답을 주는 가짜 (외부 호출 없음), 응답 값은 지어낸 것
 */
class SourceTextRecovererTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneOffset.UTC);
    private static final String RSS_FEED_URL = "https://feed.example.test/news";
    private static final String KEV_FEED_URL = "https://feed.example.test/kev.json";
    private static final String GHSA_FEED_URL = "https://api.example.test/advisories?type=reviewed&severity=critical";

    private final FeedHttpClient feedHttpClient = mock(FeedHttpClient.class);
    private final SourceTextRecoverer recoverer = new SourceTextRecoverer(feedHttpClient, CLOCK);

    @Test
    void recoversRssItemsStillInFeedByOriginalUrlAndLeavesGoneItemsOut() {
        when(feedHttpClient.fetchIfChanged(eq(RSS_FEED_URL), any())).thenReturn(FeedFetchResult.modified("""
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><title>Example News</title>
                <item><title>Example headline 1</title><link>https://news.example.test/articles/1#comments</link>
                <description><![CDATA[<p>Attackers are exploiting a flaw in <b>Example VPN</b> to steal session tokens.</p>]]></description></item>
                <item><title>Example headline 3</title><link>https://news.example.test/articles/3</link>
                <description>Too short.</description></item>
                </channel></rss>
                """.getBytes(UTF_8), FeedValidators.NONE));

        Map<Long, String> recoveredSourceTextById = recoverer.recover(List.of(
                BackfillTestData.newsTarget(1L, null), BackfillTestData.newsTarget(2L, null), BackfillTestData.newsTarget(3L, null)));

        // 1: 피드에 있음 (운영과 같은 정리: HTML 제거, 주소의 #조각 제거 후 맞춤), 2: 피드에서 빠짐, 3: 요약할 만한 글이 아님
        assertThat(recoveredSourceTextById).containsOnlyKeys(1L);
        assertThat(recoveredSourceTextById.get(1L))
                .isEqualTo("Attackers are exploiting a flaw in Example VPN to steal session tokens.");
    }

    @Test
    void recoversKevEntriesByCveIdWithRequiredAction() {
        when(feedHttpClient.fetchIfChanged(eq(KEV_FEED_URL), any())).thenReturn(FeedFetchResult.modified("""
                {"vulnerabilities":[{"cveID":"CVE-0000-00001","vulnerabilityName":"Example Gateway Improper Authentication Vulnerability",
                "dateAdded":"2026-11-01","shortDescription":"Example Gateway contains an improper authentication vulnerability in the management API.",
                "requiredAction":"Apply mitigations per vendor instructions."}]}
                """.getBytes(UTF_8), FeedValidators.NONE));
        BackfillTarget kevTarget = BackfillTarget.fromJson(BackfillTestData.row(5L, "CISA_KEV", "VULNERABILITY", "VULNERABILITY",
                "CISA_KEV_JSON", KEV_FEED_URL, "https://nvd.example.test/vuln/detail/CVE-0000-00001", "CVE-0000-00001", null));

        Map<Long, String> recoveredSourceTextById = recoverer.recover(List.of(kevTarget));

        assertThat(recoveredSourceTextById.get(5L)).isEqualTo("Example Gateway contains an improper authentication vulnerability "
                + "in the management API. Required action: Apply mitigations per vendor instructions.");
    }

    @Test
    void fetchesSingleGhsaAdvisoryWhenItIsNotOnTheFirstPage() {
        when(feedHttpClient.fetch(startsWith(GHSA_FEED_URL), anyMap())).thenReturn("[]".getBytes(UTF_8));
        when(feedHttpClient.fetch(eq("https://api.github.com/advisories/GHSA-0000-0000-0001"), anyMap())).thenReturn("""
                {"summary":"example-auth-kit: session fixation in OAuth callback",
                "html_url":"https://github.com/advisories/GHSA-0000-0000-0001","published_at":"2026-11-18T10:00:00Z",
                "description":"The OAuth callback handler reuses the pre-login session identifier. Fixed in 3.1.4."}
                """.getBytes(UTF_8));
        BackfillTarget ghsaTarget = BackfillTarget.fromJson(BackfillTestData.row(6L, "GHSA", "VULNERABILITY", "VULNERABILITY",
                "GHSA_API", GHSA_FEED_URL, "https://github.com/advisories/GHSA-0000-0000-0001", null, null));

        Map<Long, String> recoveredSourceTextById = recoverer.recover(List.of(ghsaTarget));

        assertThat(recoveredSourceTextById.get(6L)).isEqualTo("example-auth-kit: session fixation in OAuth callback "
                + "The OAuth callback handler reuses the pre-login session identifier. Fixed in 3.1.4.");
    }

    @Test
    void keepsRecoveringOtherSourcesWhenOneSourceFails() {
        when(feedHttpClient.fetchIfChanged(eq(KEV_FEED_URL), any()))
                .thenThrow(new CustomException(ErrorCode.EXTERNAL_API_ERROR, "status=503"));
        when(feedHttpClient.fetchIfChanged(eq(RSS_FEED_URL), any())).thenReturn(FeedFetchResult.modified("""
                <rss version="2.0"><channel><title>Example News</title>
                <item><title>Example headline 1</title><link>https://news.example.test/articles/1</link>
                <description>Attackers are exploiting a flaw in Example VPN to steal session tokens.</description></item>
                </channel></rss>
                """.getBytes(UTF_8), FeedValidators.NONE));
        BackfillTarget kevTarget = BackfillTarget.fromJson(BackfillTestData.row(5L, "CISA_KEV", "VULNERABILITY", "VULNERABILITY",
                "CISA_KEV_JSON", KEV_FEED_URL, "https://nvd.example.test/vuln/detail/CVE-0000-00001", "CVE-0000-00001", null));

        Map<Long, String> recoveredSourceTextById = recoverer.recover(List.of(kevTarget, BackfillTestData.newsTarget(1L, null)));

        assertThat(recoveredSourceTextById).containsOnlyKeys(1L);
        // 기사 페이지는 읽지 않음: 피드 주소 밖의 호출 없음
        verify(feedHttpClient, never()).fetch(startsWith("https://news.example.test"));
    }
}
