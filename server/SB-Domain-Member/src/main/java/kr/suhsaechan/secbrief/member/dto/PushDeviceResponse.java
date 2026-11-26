package kr.suhsaechan.secbrief.member.dto;

import java.util.List;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.entity.QuietHours;

/**
 * 알림 기기 등록 결과: 서버에 저장된 켠 알림 종류 (enum 선언 순서, 빈 목록이면 알림 꺼짐) + 방해 금지 시간
 */
public record PushDeviceResponse(List<AlertCategory> enabledAlertCategories, QuietHoursResponse quietHours) {

    /** 방해 금지 시간 기본값(꺼짐)으로 (기기를 지웠을 때) */
    public PushDeviceResponse(List<AlertCategory> enabledAlertCategories) {
        this(enabledAlertCategories, QuietHoursResponse.from(QuietHours.disabledByDefault()));
    }
}
