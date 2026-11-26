package kr.suhsaechan.secbrief.member.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

/**
 * 방해 금지 시간 요청: 켜짐 여부, 시작·끝 (HH:mm, 기기 시간대 기준), 기기 시간대 (IANA 이름)
 * 시작 == 끝(켰을 때)·모르는 시간대는 서비스가 거부 (QUIET_HOURS_SAME_TIME, INVALID_TIME_ZONE)
 */
public record QuietHoursRequest(
        @NotNull(message = "방해 금지 시간을 켤지 보내 주세요.")
        @JsonProperty("isEnabled") Boolean isEnabled,

        @NotNull(message = "시작 시각을 보내 주세요.")
        @JsonFormat(pattern = TIME_PATTERN) LocalTime startTime,

        @NotNull(message = "끝 시각을 보내 주세요.")
        @JsonFormat(pattern = TIME_PATTERN) LocalTime endTime,

        @NotBlank(message = "기기 시간대를 보내 주세요.")
        @Size(max = MAXIMUM_TIME_ZONE_LENGTH, message = "기기 시간대가 너무 길어요.")
        String timeZone
) {

    // 앱과 주고받는 시각 모양 (예: 22:00)
    public static final String TIME_PATTERN = "HH:mm";
    // push_device.time_zone 열 길이
    public static final int MAXIMUM_TIME_ZONE_LENGTH = 64;
}
