package kr.suhsaechan.secbrief.application.notification.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.dto.DigestSettingsRequest;
import kr.suhsaechan.secbrief.member.dto.InterestsRequest;
import kr.suhsaechan.secbrief.member.dto.QuietHoursRequest;

/**
 * 어제 기준 알림 추정 요청 (기기 등록 없이 계산만): 켠 종류, 관심 기술, 받는 방식(없으면 아침 요약), 기기 시간대
 */
public record AlertEstimateRequest(
        @NotNull(message = "켤 알림 종류를 보내 주세요.")
        Set<AlertCategory> enabledAlertCategories,

        @Valid
        InterestsRequest interests,

        @Valid
        DigestSettingsRequest digest,

        @NotBlank(message = "기기 시간대를 보내 주세요.")
        @Size(max = QuietHoursRequest.MAXIMUM_TIME_ZONE_LENGTH, message = "기기 시간대가 너무 길어요.")
        String timeZone
) {
}
