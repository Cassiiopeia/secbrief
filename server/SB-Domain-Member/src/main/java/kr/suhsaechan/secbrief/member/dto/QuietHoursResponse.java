package kr.suhsaechan.secbrief.member.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalTime;
import kr.suhsaechan.secbrief.member.entity.QuietHours;

/**
 * 저장된 방해 금지 시간: 켜짐 여부, 시작·끝 (HH:mm), 기기 시간대
 */
public record QuietHoursResponse(
        @JsonProperty("isEnabled") boolean isEnabled,
        @JsonFormat(pattern = QuietHoursRequest.TIME_PATTERN) LocalTime startTime,
        @JsonFormat(pattern = QuietHoursRequest.TIME_PATTERN) LocalTime endTime,
        String timeZone
) {

    public static QuietHoursResponse from(QuietHours quietHours) {
        return new QuietHoursResponse(quietHours.isEnabled(), quietHours.getStartTime(), quietHours.getEndTime(),
                quietHours.getTimeZone());
    }
}
