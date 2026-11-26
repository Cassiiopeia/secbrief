import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { StyleSheet, View } from 'react-native';
import Animated from 'react-native-reanimated';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbRadius, sbSpacing } from '../tokens';
import { sbRevealEntering } from './sb-layout-motion';
import { SbText } from './sb-text';

const NOTICE_ICON_SIZE = 16;

/** 구역 안 안내 한 줄 (누름 없음): 정보 아이콘 + 문구. 목록 행과 구분되게 옅은 바탕 상자, 붙을 때 살짝 내려오며 나타남 */
export function SbInlineNotice({ message }: { message: string }) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <Animated.View style={styles.wrap} entering={sbRevealEntering}>
      <View style={styles.notice} accessible accessibilityLabel={message}>
        <MaterialIcons name="info-outline" size={NOTICE_ICON_SIZE} color={colors.tertiaryLabel} />
        <View style={styles.text}>
          <SbText variant="caption" color={colors.secondaryLabel}>
            {message}
          </SbText>
        </View>
      </View>
    </Animated.View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    wrap: { paddingHorizontal: sbSpacing.tileHorizontal, paddingVertical: sbSpacing.small },
    notice: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: sbSpacing.small,
      paddingHorizontal: sbSpacing.compact,
      paddingVertical: sbSpacing.compact,
      borderRadius: sbRadius.button,
      backgroundColor: colors.surfaceRaised,
    },
    text: { flex: 1 },
  });
