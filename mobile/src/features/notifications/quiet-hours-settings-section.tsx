import { SbInlineErrorState, SbInlineNotice, SbSwitchRow, SbTimePickerRow } from '@/design';

import { QUIET_HOURS_EXPLANATION, QUIET_HOURS_SAME_TIME_MESSAGE, toQuietHoursSectionView } from './quiet-hours-view-state';
import { useAlertCategorySettings } from './use-alert-category-settings';
import { useQuietHoursSettings } from './use-quiet-hours-settings';

const QUIET_HOURS_TITLE = '방해 금지 시간';

/**
 * 설정 탭 방해 금지 시간: 스위치 > (켰을 때) 시작·끝 시각 + 보류 알림 안내 > 상태 안내 (알림 종류 없음 / 같은 시각 / 저장 실패)
 * 알림을 쓸 수 없는 기기(웹 등)와 알림 종류가 모두 꺼진 기기에서는 잠금
 */
export function QuietHoursSettingsSection() {
  const alertCategorySettings = useAlertCategorySettings();
  const settings = useQuietHoursSettings();
  const hasEnabledAlertCategory = alertCategorySettings.enabledAlertCategories.length > 0;
  const sectionView = toQuietHoursSectionView(settings.quietHours, hasEnabledAlertCategory);
  const isLocked =
    alertCategorySettings.unavailableReason !== undefined || !hasEnabledAlertCategory || settings.isSaving || alertCategorySettings.isSaving;

  return (
    <>
      <SbSwitchRow
        title={QUIET_HOURS_TITLE}
        detail={sectionView.switchDetail}
        isOn={sectionView.isOn}
        isDisabled={isLocked}
        accessibilityLabel={`${QUIET_HOURS_TITLE}, ${sectionView.switchDetail}`}
        onToggled={settings.onQuietHoursToggled}
      />
      {sectionView.isOn && (
        <>
          <SbTimePickerRow
            title={sectionView.startLabel}
            timeOfDay={settings.quietHours.startTime}
            timeLabel={sectionView.startTimeLabel}
            isDisabled={isLocked}
            accessibilityLabel={`${QUIET_HOURS_TITLE} ${sectionView.startLabel} ${sectionView.startTimeLabel}, 바꾸기`}
            onTimeSelected={(timeOfDay) => settings.onTimeSelected('start', timeOfDay)}
          />
          <SbTimePickerRow
            title={sectionView.endLabel}
            timeOfDay={settings.quietHours.endTime}
            timeLabel={sectionView.endTimeLabel}
            isDisabled={isLocked}
            accessibilityLabel={`${QUIET_HOURS_TITLE} ${sectionView.endLabel} ${sectionView.endTimeLabel}, 바꾸기`}
            onTimeSelected={(timeOfDay) => settings.onTimeSelected('end', timeOfDay)}
          />
          <SbInlineNotice message={QUIET_HOURS_EXPLANATION} />
        </>
      )}
      {renderStatusNotice()}
    </>
  );

  function renderStatusNotice() {
    // 알림을 쓸 수 없는 이유는 알림 종류 구역이 이미 보여 줌
    if (alertCategorySettings.unavailableReason !== undefined) return null;
    if (sectionView.unavailableHint !== undefined) return <SbInlineNotice message={sectionView.unavailableHint} />;
    if (settings.isSameTimeRejected) return <SbInlineNotice message={QUIET_HOURS_SAME_TIME_MESSAGE} />;
    if (settings.saveFailure !== undefined) {
      return (
        <SbInlineErrorState
          message={settings.saveFailure.userMessage}
          errorCode={settings.saveFailure.errorCode}
          onRetry={settings.onSaveRetried}
        />
      );
    }
    return null;
  }
}
