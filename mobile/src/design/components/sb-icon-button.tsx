import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { StyleSheet, View } from 'react-native';

import { useSbTheme } from '../theme/use-sb-theme';
import { sbSize } from '../tokens';
import type { SbIconName } from './sb-bottom-tab-bar';
import { SbPressable } from './sb-pressable';
import { SbUnreadDot } from './sb-unread-dot';

interface SbIconButtonProps {
  iconName: SbIconName;
  accessibilityLabel: string;
  onPress: () => void;
  /** 아이콘 오른쪽 위 안 본 글 점 (예: 알림 종) */
  hasDot?: boolean;
}

const ICON_SIZE = 24;

/** 아이콘 버튼 (검색, 뒤로, 지우기, 알림 종): 보이는 것은 아이콘 (+ 안 본 글 점), 터치 영역은 최소 터치 크기 */
export function SbIconButton({ iconName, accessibilityLabel, onPress, hasDot = false }: SbIconButtonProps) {
  const { colors } = useSbTheme();
  return (
    <SbPressable accessibilityLabel={accessibilityLabel} onPress={onPress} feedback="dim">
      <View style={styles.button}>
        <View>
          <MaterialIcons name={iconName} size={ICON_SIZE} color={colors.label} />
          <View style={styles.dot}>
            <SbUnreadDot isVisible={hasDot} />
          </View>
        </View>
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
  // 아이콘 오른쪽 위 모서리에 점 (아이콘 테두리에 반쯤 걸치게)
  dot: { position: 'absolute', top: 0, right: 0 },
});
