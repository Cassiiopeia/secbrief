package kr.suhsaechan.secbrief.member.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.PushPlatform;

/**
 * 알림 기기 등록 요청: 푸시 토큰 + 플랫폼 + 켠 알림 종류 전체 (빈 목록이면 알림 끔) + 방해 금지 시간(선택)
 * quietHours 가 없으면(이전 앱) 저장된 값 유지
 */
public record PushDeviceRegisterRequest(
        @NotBlank(message = "푸시 토큰을 보내 주세요.")
        @Size(max = MAXIMUM_PUSH_TOKEN_LENGTH, message = "푸시 토큰이 너무 길어요.")
        @Pattern(regexp = EXPO_PUSH_TOKEN_PATTERN, message = "푸시 토큰 형식을 확인해 주세요.")
        String pushToken,

        @NotNull(message = "플랫폼을 보내 주세요.")
        PushPlatform platform,

        @NotNull(message = "켤 알림 종류를 보내 주세요.")
        Set<AlertCategory> enabledAlertCategories,

        @Valid
        QuietHoursRequest quietHours
) {

    /** 방해 금지 시간 없이 (이전 앱과 같은 요청) */
    public PushDeviceRegisterRequest(String pushToken, PushPlatform platform, Set<AlertCategory> enabledAlertCategories) {
        this(pushToken, platform, enabledAlertCategories, null);
    }

    // push_device.push_token 열 길이
    public static final int MAXIMUM_PUSH_TOKEN_LENGTH = 255;
    // Expo 푸시 토큰 모양: ExponentPushToken[...] 또는 ExpoPushToken[...]
    public static final String EXPO_PUSH_TOKEN_PATTERN = "^Expo(nent)?PushToken\\[[^\\[\\]\\s]+\\]$";
}
