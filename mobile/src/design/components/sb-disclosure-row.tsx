import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { StyleSheet, View } from 'react-native';

import { useSbTheme } from '../theme/use-sb-theme';
import { sbSize } from '../tokens';
import { SbPressable } from './sb-pressable';
import { SbRowFrame } from './sb-row-frame';
import { SbText } from './sb-text';

interface SbDisclosureRowProps {
  title: string;
  detail?: string;
  /** 오른쪽 작은 글자 (예: 9건) */
  trailingLabel: string;
  isExpanded: boolean;
  accessibilityLabel: string;
  onPress: () => void;
}

/** 펼치는 행 (#93 아침 요약 분야 묶음): 제목 + 설명 + 오른쪽 글자 + 펼침 화살표. 펼친 내용은 부르는 쪽이 아래에 그림 */
export function SbDisclosureRow({ title, detail, trailingLabel, isExpanded, accessibilityLabel, onPress }: SbDisclosureRowProps) {
  const { colors } = useSbTheme();
  return (
    <SbPressable accessibilityLabel={accessibilityLabel} accessibilityState={{ expanded: isExpanded }} onPress={onPress} feedback="dim">
      <SbRowFrame
        title={title}
        detail={detail}
        trailing={
          <View style={styles.trailing}>
            <SbText variant="caption" color={colors.secondaryLabel}>
              {trailingLabel}
            </SbText>
            <MaterialIcons name={isExpanded ? 'expand-less' : 'expand-more'} size={sbSize.rowIcon} color={colors.tertiaryLabel} />
          </View>
        }
      />
    </SbPressable>
  );
}

const styles = StyleSheet.create({
  trailing: { flexDirection: 'row', alignItems: 'center' },
});
