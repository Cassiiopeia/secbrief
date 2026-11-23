package kr.suhsaechan.secbrief.advisory.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * NVD 요청 주소: 게시일 범위(UTC, 오프셋 없음)·페이지 크기·시작 위치
 */
class NvdArticleCollectorTest {

    private static final Instant NOW = Instant.parse("2026-11-23T03:04:05.678Z");

    private final NvdArticleCollector collector =
            new NvdArticleCollector(Mockito.mock(FeedHttpClient.class), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void supportsNvdApiFormat() {
        assertThat(collector.supportedFeedFormat()).isEqualTo(FeedFormat.NVD_API);
    }

    @Test
    void buildsRequestUrlWithUtcDateRangeAndPaging() {
        String url = collector.toRequestUrl("https://services.nvd.nist.gov/rest/json/cves/2.0",
                Instant.parse("2026-11-16T03:04:05.678Z"), NOW, 2000);

        assertThat(url).isEqualTo("https://services.nvd.nist.gov/rest/json/cves/2.0"
                + "?pubStartDate=2026-11-16T03:04:05.678&pubEndDate=2026-11-23T03:04:05.678"
                + "&resultsPerPage=2000&startIndex=2000");
    }
}
