import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { StyleSheet, View } from 'react-native';

import { useSbTheme } from '../theme/use-sb-theme';
import { sbSize } from '../tokens';
import type { SbIconName } from './sb-bottom-tab-bar';
import { SbPressable } from './sb-pressable';
import { SbRowFrame } from './sb-row-frame';
import { SbText } from './sb-text';

interface SbListRowProps {
  title: string;
  detail?: string;
  /** 오른쪽 아이콘 (예: chevron-right, open-in-new) */
  trailingIconName?: SbIconName;
  /** 아이콘 앞 작은 글자 (예: 6개) */
  trailingLabel?: string;
  accessibilityLabel: string;
  /** 없으면 누름 반응 없음 (정보만 보여 주는 행) */
  onPress?: () => void;
}

/** 설정·목록 행: 제목 + (설명) + (오른쪽 아이콘) + 아래 구분선 */
export function SbListRow({ title, detail, trailingIconName, trailingLabel, accessibilityLabel, onPress }: SbListRowProps) {
  const { colors } = useSbTheme();
  const trailingIcon = (trailingIconName !== undefined || trailingLabel !== undefined) && (
    <View style={styles.trailing}>
      {trailingLabel !== undefined && (
        <SbText variant="caption" color={colors.secondaryLabel}>
          {trailingLabel}
        </SbText>
      )}
      {trailingIconName !== undefined && <MaterialIcons name={trailingIconName} size={sbSize.rowIcon} color={colors.tertiaryLabel} />}
    </View>
  );
  if (onPress === undefined) {
    return <SbRowFrame title={title} detail={detail} trailing={trailingIcon} accessibilityLabel={accessibilityLabel} />;
  }
  return (
    <SbPressable accessibilityLabel={accessibilityLabel} onPress={onPress} feedback="dim">
      <SbRowFrame title={title} detail={detail} trailing={trailingIcon} />
    </SbPressable>
  );
}

const styles = StyleSheet.create({
  trailing: { flexDirection: 'row', alignItems: 'center' },
});
