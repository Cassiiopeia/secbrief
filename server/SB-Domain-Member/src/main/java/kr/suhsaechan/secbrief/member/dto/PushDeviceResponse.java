package kr.suhsaechan.secbrief.member.dto;

import java.util.List;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.entity.AlertInterests;
import kr.suhsaechan.secbrief.member.entity.QuietHours;

/**
 * 알림 기기 등록 결과: 서버에 저장된 켠 알림 종류 (enum 선언 순서) + 방해 금지 시간 + 관심 기술 + 받는 방식
 */
public record PushDeviceResponse(List<AlertCategory> enabledAlertCategories, QuietHoursResponse quietHours,
                                 InterestsResponse interests, DigestSettingsResponse digest) {

    /** 기본값으로 (기기를 지웠을 때: 방해 금지 꺼짐, 관심 기술 없음, 아침 요약 08:00) */
    public static PushDeviceResponse unregistered() {
        return new PushDeviceResponse(List.of(), QuietHoursResponse.from(QuietHours.disabledByDefault()),
                InterestsResponse.from(AlertInterests.none()), DigestSettingsResponse.defaultSettings());
    }
}
