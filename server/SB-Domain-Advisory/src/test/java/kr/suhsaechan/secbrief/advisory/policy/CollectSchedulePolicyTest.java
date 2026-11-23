package kr.suhsaechan.secbrief.advisory.policy;

import static kr.suhsaechan.secbrief.advisory.policy.CollectSchedulePolicy.isCollectDue;
import static kr.suhsaechan.secbrief.advisory.policy.CollectSchedulePolicy.toCollectSince;
import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.source;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import org.junit.jupiter.api.Test;

/**
 * 수집 주기 판정: 마지막 시도 기준 주기 확인, 출처별 수집 시작 시각 계산
 */
class CollectSchedulePolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");

    private final Source kisaSource = source(SourceCode.KISA, ArticleKind.ADVISORY, ArticleRegion.DOMESTIC, FeedFormat.RSS, null);
    private final Source kevSourceNeverCollected = vulnerabilitySource(SourceCode.CISA_KEV, FeedFormat.CISA_KEV_JSON, null);
    private final Source nvdSourceNeverCollected = vulnerabilitySource(SourceCode.NVD, FeedFormat.NVD_API, null);
    private final Source ghsaSourceNeverCollected = vulnerabilitySource(SourceCode.GHSA, FeedFormat.GHSA_API, null);

    @Test
    void isCollectDueWhenNeverAttempted() {
        assertThat(isCollectDue(null, 30, NOW)).isTrue();
    }

    @Test
    void isCollectDueUsesLastAttemptNotLastSuccess() {
        assertThat(isCollectDue(NOW.minus(Duration.ofMinutes(29)), 30, NOW)).isFalse();
        assertThat(isCollectDue(NOW.minus(Duration.ofMinutes(30)), 30, NOW)).isTrue();
    }

    @Test
    void firstKevCollectStartsThirtyDaysAgo() {
        assertThat(toCollectSince(kevSourceNeverCollected, NOW)).isEqualTo(NOW.minus(Duration.ofDays(30)));
    }

    @Test
    void firstNvdCollectStartsSevenDaysAgo() {
        assertThat(toCollectSince(nvdSourceNeverCollected, NOW)).isEqualTo(NOW.minus(Duration.ofDays(7)));
    }

    @Test
    void firstGhsaCollectStartsSevenDaysAgo() {
        assertThat(toCollectSince(ghsaSourceNeverCollected, NOW)).isEqualTo(NOW.minus(Duration.ofDays(7)));
    }

    @Test
    void laterNvdCollectOverlapsOneDay() {
        Instant lastCollectedAt = NOW.minus(Duration.ofHours(2));
        Source nvdSource = vulnerabilitySource(SourceCode.NVD, FeedFormat.NVD_API, lastCollectedAt);

        assertThat(toCollectSince(nvdSource, NOW)).isEqualTo(lastCollectedAt.minus(Duration.ofDays(1)));
    }

    @Test
    void laterKevCollectOverlapsOneDay() {
        Instant lastCollectedAt = NOW.minus(Duration.ofDays(3));
        Source kevSource = vulnerabilitySource(SourceCode.CISA_KEV, FeedFormat.CISA_KEV_JSON, lastCollectedAt);

        assertThat(toCollectSince(kevSource, NOW)).isEqualTo(lastCollectedAt.minus(Duration.ofDays(1)));
    }

    @Test
    void longOutageNvdCollectIsCappedAtSevenDays() {
        Source nvdSource = vulnerabilitySource(SourceCode.NVD, FeedFormat.NVD_API, NOW.minus(Duration.ofDays(30)));

        assertThat(toCollectSince(nvdSource, NOW)).isEqualTo(NOW.minus(Duration.ofDays(7)));
    }

    @Test
    void longOutageGhsaCollectIsCappedAtSevenDays() {
        Source ghsaSource = vulnerabilitySource(SourceCode.GHSA, FeedFormat.GHSA_API, NOW.minus(Duration.ofDays(30)));

        assertThat(toCollectSince(ghsaSource, NOW)).isEqualTo(NOW.minus(Duration.ofDays(7)));
    }

    @Test
    void rssHasNoSince() {
        assertThat(toCollectSince(kisaSource, NOW)).isNull();
    }

    @Test
    void rssHasNoSinceEvenAfterEarlierCollect() {
        Source collectedKisaSource = source(SourceCode.KISA, ArticleKind.ADVISORY, ArticleRegion.DOMESTIC,
                FeedFormat.RSS, NOW.minus(Duration.ofHours(1)));

        assertThat(toCollectSince(collectedKisaSource, NOW)).isNull();
    }

    private static Source vulnerabilitySource(String code, FeedFormat feedFormat, Instant lastCollectedAt) {
        return source(code, ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, feedFormat, lastCollectedAt);
    }
}
