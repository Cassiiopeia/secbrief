import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbRadius, sbSize, sbSpacing } from '../tokens';
import { playSbHaptic } from './sb-haptics';
import { SbPressable } from './sb-pressable';
import { SbRowFrame } from './sb-row-frame';
import { SbText } from './sb-text';

export interface SbPickOption {
  value: string;
  label: string;
}

/** 여러 개 고르는 칩 묶음 (#93 관심 기술 제품): 줄바꿈 격자, 고른 칩은 본문색 채움 + 체크 */
export function SbPickChips({ options, pickedValues, onToggled }: { options: SbPickOption[]; pickedValues: string[]; onToggled: (value: string) => void }) {
  return (
    <View style={gridStyles.grid}>
      {options.map((option) => (
        <SbPickButton
          key={option.value}
          label={option.label}
          isPicked={pickedValues.includes(option.value)}
          onPress={() => onToggled(option.value)}
        />
      ))}
    </View>
  );
}

interface SbPickButtonProps {
  label: string;
  isPicked: boolean;
  accessibilityLabel?: string;
  onPress: () => void;
}

/** 고르기 알약 하나: 고르면 체크 + 채움 (행 오른쪽 받기·안 받기 버튼도 이것) */
export function SbPickButton({ label, isPicked, accessibilityLabel, onPress }: SbPickButtonProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <SbPressable
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityRole="checkbox"
      accessibilityState={{ checked: isPicked }}
      onPress={() => {
        playSbHaptic('selection');
        onPress();
      }}>
      <View style={[styles.pick, isPicked ? styles.picked : styles.unpicked]}>
        {isPicked && <MaterialIcons name="check" size={sbSize.pickIcon} color={colors.background} />}
        <SbText variant="button" color={isPicked ? colors.background : colors.label}>
          {label}
        </SbText>
      </View>
    </SbPressable>
  );
}

interface SbPickRowProps {
  title: string;
  detail?: string;
  isPicked: boolean;
  /** 고른 상태 / 안 고른 상태의 버튼 글자 (예: 받기 / 안 받기) */
  pickedLabel: string;
  unpickedLabel: string;
  onToggled: () => void;
}

/** 고르기 행 (#93 관심 분야·생태계): 제목 + 설명 + 오른쪽 고르기 알약 + 아래 구분선 */
export function SbPickRow({ title, detail, isPicked, pickedLabel, unpickedLabel, onToggled }: SbPickRowProps) {
  return (
    <SbRowFrame
      title={title}
      detail={detail}
      trailing={
        <SbPickButton
          label={isPicked ? pickedLabel : unpickedLabel}
          isPicked={isPicked}
          accessibilityLabel={`${title}, ${isPicked ? pickedLabel : unpickedLabel}`}
          onPress={onToggled}
        />
      }
    />
  );
}

const gridStyles = StyleSheet.create({
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: sbSpacing.small, paddingHorizontal: sbSpacing.pageHorizontal, paddingBottom: sbSpacing.small },
});

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    pick: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: sbSpacing.extraSmall,
      minHeight: sbSize.pickHeight,
      paddingHorizontal: sbSpacing.compact,
      borderRadius: sbRadius.pill,
      borderWidth: StyleSheet.hairlineWidth,
    },
    picked: { backgroundColor: colors.label, borderColor: colors.label },
    unpicked: { backgroundColor: colors.surface, borderColor: colors.separator },
  });
