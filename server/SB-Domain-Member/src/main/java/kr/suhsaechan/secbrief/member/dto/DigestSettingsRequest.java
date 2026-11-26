package kr.suhsaechan.secbrief.member.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import kr.suhsaechan.secbrief.member.constant.AlertDeliveryMode;

/**
 * 긴급이 아닌 관심 소식 받는 방식: 바로(IMMEDIATE) 또는 아침 요약(DIGEST) + 요약 시각 (HH:mm, 기기 시간대)
 */
public record DigestSettingsRequest(
        @NotNull(message = "받는 방식을 보내 주세요.")
        AlertDeliveryMode deliveryMode,

        @NotNull(message = "요약 시각을 보내 주세요.")
        @JsonFormat(pattern = QuietHoursRequest.TIME_PATTERN) LocalTime digestTime
) {
}
