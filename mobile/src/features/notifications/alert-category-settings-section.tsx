import { Linking } from 'react-native';

import { SbInlineErrorState, SbInlineNotice, SbListRow, SbReveal, SbSwitchRow } from '@/design';

import { toAlertCategoryRowViews, toPushUnavailableMessage } from './alert-category-view-state';
import { useAlertCategorySettings } from './use-alert-category-settings';

/**
 * 설정 탭 알림 구역: 종류별 스위치 3개 + 상태 안내 1개 (쓸 수 없음 / 권한 거부 / 저장 실패)
 */
export function AlertCategorySettingsSection() {
  const settings = useAlertCategorySettings();
  const isUnavailable = settings.unavailableReason !== undefined;

  return (
    <>
      {toAlertCategoryRowViews(settings.enabledAlertCategories).map((rowView) => (
        <SbSwitchRow
          key={rowView.category}
          title={rowView.title}
          detail={rowView.detail}
          isOn={rowView.isOn}
          // 저장 중에는 잠금 (요청이 겹치면 서버와 기기 값이 어긋남)
          isDisabled={isUnavailable || settings.isSaving}
          accessibilityLabel={`${rowView.title} 알림, ${rowView.detail}`}
          onToggled={(isOn) => settings.onAlertCategoryToggled(rowView.category, isOn)}
        />
      ))}
      {renderStatusNotice()}
    </>
  );

  function renderStatusNotice() {
    if (settings.unavailableReason !== undefined) {
      return <SbInlineNotice message={toPushUnavailableMessage(settings.unavailableReason)} />;
    }
    if (settings.isPermissionDenied) {
      return (
        <SbReveal>
          <SbListRow
            title="기기 설정에서 알림을 허용해 주세요"
            detail="허용한 뒤 스위치를 다시 켜 주세요."
            trailingIconName="open-in-new"
            accessibilityLabel="기기 설정에서 알림 허용하기"
            onPress={() => Linking.openSettings()}
          />
        </SbReveal>
      );
    }
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
