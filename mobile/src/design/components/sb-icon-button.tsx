import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { StyleSheet, View } from 'react-native';

import { useSbTheme } from '../theme/use-sb-theme';
import { sbSize } from '../tokens';
import type { SbIconName } from './sb-bottom-tab-bar';
import { SbPressable } from './sb-pressable';

interface SbIconButtonProps {
  iconName: SbIconName;
  accessibilityLabel: string;
  onPress: () => void;
}

const ICON_SIZE = 24;

/** 아이콘 버튼 (검색, 뒤로, 지우기): 보이는 것은 아이콘, 터치 영역은 최소 터치 크기 */
export function SbIconButton({ iconName, accessibilityLabel, onPress }: SbIconButtonProps) {
  const { colors } = useSbTheme();
  return (
    <SbPressable accessibilityLabel={accessibilityLabel} onPress={onPress} feedback="dim">
      <View style={styles.button}>
        <MaterialIcons name={iconName} size={ICON_SIZE} color={colors.label} />
      </View>
    </SbPressable>
  );
}

const styles = StyleSheet.create({
  button: {
    width: sbSize.minimumTouchTarget,
    height: sbSize.minimumTouchTarget,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
