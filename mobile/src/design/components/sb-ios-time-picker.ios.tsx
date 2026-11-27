import { DatePicker, Host } from '@expo/ui/swift-ui';
import { accessibilityLabel as accessibilityLabelModifier, datePickerStyle, disabled, environment } from '@expo/ui/swift-ui/modifiers';

import type { SbIosTimePickerProps } from './sb-ios-time-picker-props';

/**
 * iOS 행 안 작은 시각 선택기 (SwiftUI DatePicker 직접): 읽기 도우미 문구를 선택기 자체에 붙임
 * community 선택기는 접근성 라벨을 넘기지 못해 시각 글자가 빠지는 행이 생김 (#119)
 */
export function SbIosTimePicker({ selectedDate, isDisabled, isDark, accessibilityLabel, onDateSelected }: SbIosTimePickerProps) {
  return (
    <Host matchContents ignoreSafeArea="all">
      <DatePicker
        selection={selectedDate}
        displayedComponents={['hourAndMinute']}
        onDateChange={onDateSelected}
        modifiers={[
          datePickerStyle('compact'),
          disabled(isDisabled),
          environment('locale', 'ko_KR'),
          environment('colorScheme', isDark ? 'dark' : 'light'),
          accessibilityLabelModifier(accessibilityLabel),
        ]}
      />
    </Host>
  );
}
