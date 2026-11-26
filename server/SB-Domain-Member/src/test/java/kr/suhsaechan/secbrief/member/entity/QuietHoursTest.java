package kr.suhsaechan.secbrief.member.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalTime;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.member.dto.QuietHoursRequest;
import kr.suhsaechan.secbrief.member.service.PushDeviceService;
import org.junit.jupiter.api.Test;

/**
 * 방해 금지 시간 계산: 같은 날 구간, 자정 넘김, 경계([시작, 끝)), 시작 == 끝, 꺼짐, 기기 시간대, 요청 검증
 */
class QuietHoursTest {

    private static final LocalTime TEN_PM = LocalTime.of(22, 0);
    private static final LocalTime EIGHT_AM = LocalTime.of(8, 0);

    @Test
    void containsTimesInsideSameDayWindow() {
        LocalTime lunchStart = LocalTime.of(12, 0);
        LocalTime lunchEnd = LocalTime.of(13, 0);

        assertThat(QuietHours.isWithinWindow(lunchStart, lunchEnd, LocalTime.of(12, 0))).isTrue();
        assertThat(QuietHours.isWithinWindow(lunchStart, lunchEnd, LocalTime.of(12, 59))).isTrue();
        // 끝 시각은 포함하지 않음
        assertThat(QuietHours.isWithinWindow(lunchStart, lunchEnd, LocalTime.of(13, 0))).isFalse();
        assertThat(QuietHours.isWithinWindow(lunchStart, lunchEnd, LocalTime.of(11, 59))).isFalse();
    }

    @Test
    void containsTimesOnBothSidesOfMidnightForCrossingWindow() {
        assertThat(QuietHours.isWithinWindow(TEN_PM, EIGHT_AM, LocalTime.of(22, 0))).isTrue();
        assertThat(QuietHours.isWithinWindow(TEN_PM, EIGHT_AM, LocalTime.of(23, 59))).isTrue();
        assertThat(QuietHours.isWithinWindow(TEN_PM, EIGHT_AM, LocalTime.MIDNIGHT)).isTrue();
        assertThat(QuietHours.isWithinWindow(TEN_PM, EIGHT_AM, LocalTime.of(7, 59))).isTrue();
        assertThat(QuietHours.isWithinWindow(TEN_PM, EIGHT_AM, LocalTime.of(8, 0))).isFalse();
        assertThat(QuietHours.isWithinWindow(TEN_PM, EIGHT_AM, LocalTime.of(15, 0))).isFalse();
    }

    @Test
    void treatsSameStartAndEndAsEmptyWindow() {
        assertThat(QuietHours.isWithinWindow(TEN_PM, TEN_PM, TEN_PM)).isFalse();
        assertThat(new QuietHours(true, TEN_PM, TEN_PM, "Asia/Seoul").isActiveAt(Instant.now())).isFalse();
    }

    @Test
    void usesDeviceTimeZoneAndIgnoresDisabledSetting() {
        // 14:30 UTC = 서울 23:30 (방해 금지 안), 런던 14:30 (밖)
        Instant instant = Instant.parse("2026-11-27T14:30:00Z");

        assertThat(new QuietHours(true, TEN_PM, EIGHT_AM, "Asia/Seoul").isActiveAt(instant)).isTrue();
        assertThat(new QuietHours(true, TEN_PM, EIGHT_AM, "Europe/London").isActiveAt(instant)).isFalse();
        assertThat(new QuietHours(false, TEN_PM, EIGHT_AM, "Asia/Seoul").isActiveAt(instant)).isFalse();
    }

    @Test
    void defaultsToDisabledTenPmToEightAmInSeoul() {
        QuietHours quietHours = QuietHours.disabledByDefault();

        assertThat(quietHours.isEnabled()).isFalse();
        assertThat(quietHours.getStartTime()).isEqualTo(TEN_PM);
        assertThat(quietHours.getEndTime()).isEqualTo(EIGHT_AM);
        assertThat(quietHours.getTimeZone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void rejectsSameTimeWhenEnabledAndUnknownTimeZone() {
        assertThatThrownBy(() -> PushDeviceService.toQuietHours(new QuietHoursRequest(true, TEN_PM, TEN_PM, "Asia/Seoul")))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.QUIET_HOURS_SAME_TIME));
        assertThatThrownBy(() -> PushDeviceService.toQuietHours(new QuietHoursRequest(true, TEN_PM, EIGHT_AM, "Mars/Base")))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TIME_ZONE));
        // 꺼져 있으면 시작 == 끝도 저장 (보류에 쓰지 않음)
        assertThat(PushDeviceService.toQuietHours(new QuietHoursRequest(false, TEN_PM, TEN_PM, "Asia/Seoul")).isEnabled())
                .isFalse();
    }
}
