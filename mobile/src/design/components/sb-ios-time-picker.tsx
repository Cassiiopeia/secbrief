import type { SbIosTimePickerProps } from './sb-ios-time-picker-props';

/** iOS 밖에서는 그리지 않음 (SwiftUI 선택기 모듈을 웹·Android 묶음에 넣지 않으려고 파일을 나눔, 실제 선택기는 .ios.tsx) */
export function SbIosTimePicker(_props: SbIosTimePickerProps) {
  return null;
}
