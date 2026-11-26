package kr.suhsaechan.secbrief.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.AlertDeliveryMode;
import kr.suhsaechan.secbrief.member.constant.PushPlatform;
import org.junit.jupiter.api.Test;

/**
 * 아침 요약 시각 판정: 기기 시간대의 요약 시각 이후, 하루 1번, 방해 금지 시간이면 끝난 뒤 / 관심 기술 교체·구독 없음 판정
 */
class PushDeviceDigestTest {

    // 2026-11-27 08:05 KST = 2026-11-26 23:05 UTC
    private static final Instant SEOUL_MORNING = Instant.parse("2026-11-26T23:05:00Z");

    @Test
    void isDueAfterDigestTimeInDeviceTimeZoneOncePerDay() {
        PushDevice seoulDevice = createDevice("Asia/Seoul");
        PushDevice newYorkDevice = createDevice("America/New_York");

        assertThat(seoulDevice.isDigestDueAt(SEOUL_MORNING.minusSeconds(10 * 60))).isFalse();
        assertThat(seoulDevice.isDigestDueAt(SEOUL_MORNING)).isTrue();
        // 같은 순간 뉴욕은 전날 18:05 (아침 8시 이후, 그 날짜에 아직 안 보냄)
        assertThat(newYorkDevice.isDigestDueAt(SEOUL_MORNING)).isTrue();

        seoulDevice.markDigestSent(SEOUL_MORNING);
        assertThat(seoulDevice.isDigestDueAt(SEOUL_MORNING.plusSeconds(3600))).isFalse();
        // 다음 날 08:00 이후 다시
        assertThat(seoulDevice.isDigestDueAt(SEOUL_MORNING.plusSeconds(24 * 3600))).isTrue();
    }

    @Test
    void waitsUntilQuietHoursEndAndUsesChosenTime() {
        PushDevice device = createDevice("Asia/Seoul");
        device.updateQuietHours(new QuietHours(true, LocalTime.of(23, 0), LocalTime.of(9, 0), "Asia/Seoul"));

        // 08:05 는 방해 금지 시간 안 > 09:00 이후 첫 틱에 보냄
        assertThat(device.isDigestDueAt(SEOUL_MORNING)).isFalse();
        assertThat(device.isDigestDueAt(Instant.parse("2026-11-27T00:00:00Z"))).isTrue();

        device.updateDigestSettings(AlertDeliveryMode.DIGEST, LocalTime.of(10, 30));
        assertThat(device.isDigestDueAt(Instant.parse("2026-11-27T00:00:00Z"))).isFalse();
    }

    @Test
    void replacesInterestsAndKeepsRowWhileInterestsRemain() {
        PushDevice device = PushDevice.create("ExponentPushToken[a]", PushPlatform.IOS, EnumSet.noneOf(AlertCategory.class));
        device.updateInterests(new AlertInterests(Set.of("fortinet", "chrome"), Set.of("network"), Set.of()));
        device.updateInterests(new AlertInterests(Set.of("chrome"), Set.of(), Set.of("maven")));

        assertThat(device.getAlertInterests()).isEqualTo(new AlertInterests(Set.of("chrome"), Set.of(), Set.of("maven")));
        assertThat(device.hasNoAlertSubscription()).isFalse();
        device.updateInterests(AlertInterests.none());
        assertThat(device.hasNoAlertSubscription()).isTrue();
    }

    private static PushDevice createDevice(String timeZone) {
        PushDevice device = PushDevice.create("ExponentPushToken[" + timeZone + "]", PushPlatform.ANDROID,
                EnumSet.of(AlertCategory.KEV));
        device.updateQuietHours(new QuietHours(false, QuietHours.DEFAULT_START_TIME, QuietHours.DEFAULT_END_TIME, timeZone));
        return device;
    }
}
