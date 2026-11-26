package kr.suhsaechan.secbrief.advisory.indicator;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import kr.suhsaechan.secbrief.advisory.collector.FeedHttpClient;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 해외 지표: 실제 응답 모양(CIS 텍스트 피드, SANS InfoCon JSON) > 단계 값·위치, 모르는 값·깨진 응답은 버림
 * 보관소: 지표마다 격리, 실패하면 이전 값 유지, 한 번도 못 읽으면 없음
 */
class InternationalAlertIndicatorStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-27T00:00:00Z");

    private final FeedHttpClient feedHttpClient = mock(FeedHttpClient.class);
    private final InternationalAlertIndicatorStore store =
            new InternationalAlertIndicatorStore(feedHttpClient, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void parsesRealResponseShapes() {
        assertThat(InternationalAlertIndicatorParser.toLevelKey(InternationalAlertIndicator.CIS_MS_ISAC, readFixture("cis-text-feed.txt")))
                .contains("elevated");
        assertThat(InternationalAlertIndicatorParser.toLevelKey(InternationalAlertIndicator.SANS_ISC_INFOCON, readFixture("sans-infocon.json")))
                .contains("green");
        assertThat(InternationalAlertIndicator.CIS_MS_ISAC.toLevelStep("elevated")).isEqualTo(3);
        assertThat(InternationalAlertIndicator.SANS_ISC_INFOCON.toLevelStep("green")).isEqualTo(1);
    }

    @Test
    void rejectsUnknownLevelAndBrokenBody() {
        // blue 는 SANS 의 시험용 단계
        assertThat(InternationalAlertIndicatorParser.toLevelKey(InternationalAlertIndicator.SANS_ISC_INFOCON,
                "{\"status\":\"blue\"}".getBytes(UTF_8))).isEmpty();
        assertThat(InternationalAlertIndicatorParser.toLevelKey(InternationalAlertIndicator.SANS_ISC_INFOCON,
                "<html>".getBytes(UTF_8))).isEmpty();
        assertThat(InternationalAlertIndicatorParser.toLevelKey(InternationalAlertIndicator.CIS_MS_ISAC,
                "\n".getBytes(UTF_8))).isEmpty();
    }

    @Test
    void keepsPreviousReadingWhenRefreshFailsAndIsolatesIndicators() {
        when(feedHttpClient.fetch(InternationalAlertIndicator.CIS_MS_ISAC.getFeedUrl())).thenReturn(readFixture("cis-text-feed.txt"));
        when(feedHttpClient.fetch(InternationalAlertIndicator.SANS_ISC_INFOCON.getFeedUrl()))
                .thenThrow(new CustomException(ErrorCode.EXTERNAL_API_ERROR, "status=503"));
        store.refreshAll();

        assertThat(store.findLatestReading(InternationalAlertIndicator.CIS_MS_ISAC))
                .contains(new InternationalAlertIndicatorReading(InternationalAlertIndicator.CIS_MS_ISAC, "elevated", NOW));
        assertThat(store.findLatestReading(InternationalAlertIndicator.SANS_ISC_INFOCON)).isEmpty();

        // 다음 틱에 CIS 실패: 이전 값과 그 시각 그대로
        when(feedHttpClient.fetch(InternationalAlertIndicator.CIS_MS_ISAC.getFeedUrl()))
                .thenThrow(new CustomException(ErrorCode.EXTERNAL_API_ERROR, "unreachable"));
        store.refreshAll();

        assertThat(store.findLatestReading(InternationalAlertIndicator.CIS_MS_ISAC)).get()
                .extracting(InternationalAlertIndicatorReading::checkedAt).isEqualTo(NOW);
    }

    private static byte[] readFixture(String fileName) {
        try (InputStream stream = InternationalAlertIndicatorStoreTest.class.getResourceAsStream("/fixtures/indicator/" + fileName)) {
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
