package kr.suhsaechan.secbrief.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기기의 방해 금지 시간: 켜짐 여부, 시작·끝 (기기 시간대 기준 [시작, 끝)), 시간대
 * 끝이 시작보다 이르면 자정을 넘김 (22:00~08:00 > 밤 10시부터 다음 날 아침 8시 전까지)
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuietHours {

    // 처음 켤 때 앱이 제안하는 값과 같음 (DB 기본값도 같음)
    public static final LocalTime DEFAULT_START_TIME = LocalTime.of(22, 0);
    public static final LocalTime DEFAULT_END_TIME = LocalTime.of(8, 0);
    public static final String DEFAULT_TIME_ZONE = "Asia/Seoul";

    @Column(name = "is_quiet_hours_enabled", nullable = false)
    private boolean isEnabled;

    @Column(name = "quiet_hours_start_time", nullable = false)
    private LocalTime startTime = DEFAULT_START_TIME;

    @Column(name = "quiet_hours_end_time", nullable = false)
    private LocalTime endTime = DEFAULT_END_TIME;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = DEFAULT_TIME_ZONE;

    /**
     * 값 검증은 호출부 (시간대 이름, 켰을 때 시작 != 끝)
     */
    public QuietHours(boolean isEnabled, LocalTime startTime, LocalTime endTime, String timeZone) {
        this.isEnabled = isEnabled;
        this.startTime = startTime;
        this.endTime = endTime;
        this.timeZone = timeZone;
    }

    public static QuietHours disabledByDefault() {
        return new QuietHours(false, DEFAULT_START_TIME, DEFAULT_END_TIME, DEFAULT_TIME_ZONE);
    }

    /**
     * 그 시각이 방해 금지 시간 안인지 (꺼져 있거나 시작 == 끝이면 항상 false)
     */
    public boolean isActiveAt(Instant instant) {
        if (!isEnabled) {
            return false;
        }
        return isWithinWindow(startTime, endTime, instant.atZone(ZoneId.of(timeZone)).toLocalTime());
    }

    /**
     * 시각이 [시작, 끝) 안인지: 시작 < 끝이면 같은 날, 시작 > 끝이면 자정을 넘김, 같으면 빈 구간
     */
    public static boolean isWithinWindow(LocalTime startTime, LocalTime endTime, LocalTime localTime) {
        if (startTime.equals(endTime)) {
            return false;
        }
        if (startTime.isBefore(endTime)) {
            return !localTime.isBefore(startTime) && localTime.isBefore(endTime);
        }
        return !localTime.isBefore(startTime) || localTime.isBefore(endTime);
    }
}
