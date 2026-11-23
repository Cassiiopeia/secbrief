import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbSpacing } from '../tokens';
import type { SbIconName } from './sb-bottom-tab-bar';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

interface SbListRowProps {
  title: string;
  detail?: string;
  /** 오른쪽 아이콘 (예: chevron-right, open-in-new) */
  trailingIconName?: SbIconName;
  accessibilityLabel: string;
  /** 없으면 누름 반응 없음 (정보만 보여 주는 행) */
  onPress?: () => void;
}

const TRAILING_ICON_SIZE = 20;
const SEPARATOR_HEIGHT = StyleSheet.hairlineWidth;

/** 설정·목록 행: 제목 + (설명) + (오른쪽 아이콘) + 아래 구분선 */
export function SbListRow({ title, detail, trailingIconName, accessibilityLabel, onPress }: SbListRowProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  const content = (
    <View style={styles.row} accessible={onPress === undefined} accessibilityLabel={accessibilityLabel}>
      <View style={styles.text}>
        <SbText variant="body" color={colors.label}>
          {title}
        </SbText>
        {detail !== undefined && (
          <SbText variant="caption" color={colors.tertiaryLabel}>
            {detail}
          </SbText>
        )}
      </View>
      {trailingIconName !== undefined && (
        <MaterialIcons name={trailingIconName} size={TRAILING_ICON_SIZE} color={colors.tertiaryLabel} />
      )}
    </View>
  );
  if (onPress === undefined) return content;
  return (
    <SbPressable accessibilityLabel={accessibilityLabel} onPress={onPress}>
      {content}
    </SbPressable>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    row: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: sbSpacing.compact,
      paddingHorizontal: sbSpacing.pageHorizontal,
      paddingVertical: sbSpacing.compact,
      borderBottomWidth: SEPARATOR_HEIGHT,
      borderBottomColor: colors.separator,
    },
    text: { flex: 1, gap: sbSpacing.extraSmall },
  });
