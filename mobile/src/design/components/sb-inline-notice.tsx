import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbRadius, sbSpacing } from '../tokens';
import { SbText } from './sb-text';

const NOTICE_ICON_SIZE = 16;

/** 구역 안 안내 한 줄 (누름 없음): 정보 아이콘 + 문구. 목록 행과 구분되게 옅은 바탕 상자 */
export function SbInlineNotice({ message }: { message: string }) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <View style={styles.wrap}>
      <View style={styles.notice} accessible accessibilityLabel={message}>
        <MaterialIcons name="info-outline" size={NOTICE_ICON_SIZE} color={colors.tertiaryLabel} />
        <View style={styles.text}>
          <SbText variant="caption" color={colors.secondaryLabel}>
            {message}
          </SbText>
        </View>
      </View>
    </View>
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
