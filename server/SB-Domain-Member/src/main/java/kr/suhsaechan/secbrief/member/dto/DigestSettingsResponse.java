package kr.suhsaechan.secbrief.member.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;
import kr.suhsaechan.secbrief.member.constant.AlertDeliveryMode;
import kr.suhsaechan.secbrief.member.entity.PushDevice;

/**
 * 저장된 받는 방식 + 요약 시각 (HH:mm)
 */
public record DigestSettingsResponse(AlertDeliveryMode deliveryMode,
                                     @JsonFormat(pattern = QuietHoursRequest.TIME_PATTERN) LocalTime digestTime) {

    public static DigestSettingsResponse from(PushDevice pushDevice) {
        return new DigestSettingsResponse(pushDevice.getAlertDeliveryMode(), pushDevice.getDigestTime());
    }

    /** 요청 값 그대로 (기기를 지울 때 돌려줌, #119) */
    public static DigestSettingsResponse from(DigestSettingsRequest request) {
        return new DigestSettingsResponse(request.deliveryMode(), request.digestTime());
    }

    /** 기기가 없을 때 기본값 (아침 요약 08:00) */
    public static DigestSettingsResponse defaultSettings() {
        return new DigestSettingsResponse(AlertDeliveryMode.DIGEST, PushDevice.DEFAULT_DIGEST_TIME);
    }
}
