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
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetchResult;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetchTarget;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetcher;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 요약 입력 되찾기: 운영 수집기·정규화로 다시 읽어 CVE ID·원문 주소로 맞춤 > 못 맞춘 건은 1건씩 다시 받기 (SourceTextRefetcher)
 * 출처 호출·다시 받기는 정해 둔 응답을 주는 가짜 (외부 호출 없음), 응답 값은 지어낸 것 (출처별 다시 받기는 SourceTextRefetcherTest)
 */
class SourceTextRecovererTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneOffset.UTC);
    private static final String RSS_FEED_URL = "https://feed.example.test/news";
    private static final String KEV_FEED_URL = "https://feed.example.test/kev.json";
    private static final String UBUNTU_FEED_URL = "https://ubuntu.example.test/rss.xml";
    private static final String GHSA_FEED_URL = "https://api.example.test/advisories?type=reviewed&severity=critical";

    private final FeedHttpClient feedHttpClient = mock(FeedHttpClient.class);
    private final SourceTextRefetcher sourceTextRefetcher = mock(SourceTextRefetcher.class);
    private final SourceTextRecoverer recoverer = new SourceTextRecoverer(feedHttpClient, CLOCK, sourceTextRefetcher);

    @BeforeEach
    void givenNothingRefetched() {
        when(sourceTextRefetcher.refetch(any())).thenReturn(SourceTextRefetchResult.notSupported());
    }

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

        Map<Long, SourceTextRefetchResult> resultById = recoverer.recover(List.of(
                BackfillTestData.newsTarget(1L, null), BackfillTestData.newsTarget(2L, null), BackfillTestData.newsTarget(3L, null)));

        // 1: 피드에 있음 (운영과 같은 정리: HTML 제거, 주소의 #조각 제거 후 맞춤), 2: 피드에서 빠짐, 3: 요약할 만한 글이 아님 (2·3 은 1건씩 받기로)
        assertThat(resultById.get(1L)).isEqualTo(
                SourceTextRefetchResult.recovered("Attackers are exploiting a flaw in Example VPN to steal session tokens."));
        assertThat(resultById.get(2L).status()).isEqualTo(SourceTextRefetchResult.Status.NOT_SUPPORTED);
        assertThat(resultById.get(3L).status()).isEqualTo(SourceTextRefetchResult.Status.NOT_SUPPORTED);
        verify(sourceTextRefetcher, never()).refetch(new SourceTextRefetchTarget("THE_HACKER_NEWS", "Example headline 1",
                "https://news.example.test/articles/1", null));
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

        Map<Long, SourceTextRefetchResult> resultById = recoverer.recover(List.of(kevTarget));

        assertThat(resultById.get(5L).sourceText()).isEqualTo("Example Gateway contains an improper authentication vulnerability "
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

        Map<Long, SourceTextRefetchResult> resultById = recoverer.recover(List.of(ghsaTarget));

        assertThat(resultById.get(6L).sourceText()).isEqualTo("example-auth-kit: session fixation in OAuth callback "
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

        Map<Long, SourceTextRefetchResult> resultById = recoverer.recover(List.of(kevTarget, BackfillTestData.newsTarget(1L, null)));

        assertThat(resultById.get(1L).isRecovered()).isTrue();
        assertThat(resultById.get(5L).isRecovered()).isFalse();
    }

    @Test
    void refetchesOneByOneWhenItemIsGoneFromFeedOrFeedFails() {
        when(feedHttpClient.fetchIfChanged(eq(UBUNTU_FEED_URL), any()))
                .thenThrow(new CustomException(ErrorCode.EXTERNAL_API_ERROR, "unreachable : SocketTimeoutException"));
        BackfillTarget ubuntuTarget = BackfillTarget.fromJson(BackfillTestData.row(7L, "UBUNTU_USN", "ADVISORY", "GENERAL", "RSS",
                UBUNTU_FEED_URL, "https://ubuntu.example.test/notices/USN-0000-1", null, null));
        SourceTextRefetchTarget ubuntuRefetchTarget = new SourceTextRefetchTarget("UBUNTU_USN", "Example headline 7",
                "https://ubuntu.example.test/notices/USN-0000-1", null);
        when(sourceTextRefetcher.refetch(ubuntuRefetchTarget))
                .thenReturn(SourceTextRefetchResult.recovered("It was discovered that Example mishandled input."));

        Map<Long, SourceTextRefetchResult> resultById = recoverer.recover(List.of(ubuntuTarget));

        // 피드 읽기가 시간 초과여도 1건씩 받기는 함
        assertThat(resultById.get(7L).sourceText()).isEqualTo("It was discovered that Example mishandled input.");
    }
}
