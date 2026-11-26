package kr.suhsaechan.secbrief.application.notification;

import java.util.EnumSet;
import java.util.Set;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.AlertDeliveryMode;
import kr.suhsaechan.secbrief.member.entity.AlertInterests;
import kr.suhsaechan.secbrief.member.entity.PushDevice;

/**
 * 알림 판정에 쓰는 기기 설정: 켠 종류(악용 중·KISA 긴급·그 밖의 고위험), 관심 기술, 받는 방식
 */
public record DeviceAlertSettings(Set<AlertCategory> enabledAlertCategories, AlertInterests interests,
                                  AlertDeliveryMode deliveryMode) {

    public DeviceAlertSettings {
        enabledAlertCategories = enabledAlertCategories.isEmpty()
                ? EnumSet.noneOf(AlertCategory.class)
                : EnumSet.copyOf(enabledAlertCategories);
    }

    public static DeviceAlertSettings from(PushDevice pushDevice) {
        return new DeviceAlertSettings(pushDevice.getEnabledAlertCategories(), pushDevice.getAlertInterests(),
                pushDevice.getAlertDeliveryMode());
    }
}
