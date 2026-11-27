/** iOS 시각 선택기 입력 (플랫폼 파일 2개가 함께 씀) */
export interface SbIosTimePickerProps {
  selectedDate: Date;
  isDisabled: boolean;
  isDark: boolean;
  /** 읽기 도우미 문구 (행 이름 + 시각) */
  accessibilityLabel: string;
  onDateSelected: (date: Date) => void;
}
