import { Switch } from 'react-native';

import { useSbTheme } from '../theme/use-sb-theme';
import { playSbHaptic } from './sb-haptics';
import { SbRowFrame } from './sb-row-frame';

interface SbSwitchRowProps {
  title: string;
  detail?: string;
  isOn: boolean;
  /** 바꿀 수 없는 상태 (스위치 흐리게, 누름 없음) */
  isDisabled?: boolean;
  accessibilityLabel: string;
  onToggled: (isOn: boolean) => void;
}

/** 설정 스위치 행: 제목 + (설명) + 오른쪽 스위치 + 아래 구분선 (SbListRow 와 같은 틀) */
export function SbSwitchRow({ title, detail, isOn, isDisabled = false, accessibilityLabel, onToggled }: SbSwitchRowProps) {
  const { colors } = useSbTheme();
  const onValueChange = (isNextOn: boolean) => {
    playSbHaptic('light');
    onToggled(isNextOn);
  };
  return (
    <SbRowFrame
      title={title}
      detail={detail}
      isTitleDimmed={isDisabled}
      trailing={
        <Switch
          value={isOn}
          disabled={isDisabled}
          onValueChange={onValueChange}
          accessibilityLabel={accessibilityLabel}
          trackColor={{ false: colors.surfaceRaised, true: colors.accent }}
          thumbColor={isOn ? colors.onAccent : colors.tertiaryLabel}
          // iOS 꺼진 궤도 바탕 (trackColor.false 가 iOS 에서는 적용되지 않음)
          ios_backgroundColor={colors.surfaceRaised}
        />
      }
    />
  );
}
