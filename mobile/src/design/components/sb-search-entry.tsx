import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbMaximumLines, sbRadius, sbSize, sbSpacing } from '../tokens';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

interface SbSearchEntryProps {
  placeholder: string;
  accessibilityLabel: string;
  onPress: () => void;
}

/** 검색 들머리: 검색 입력창처럼 보이는 버튼 (누르면 검색 화면으로) */
export function SbSearchEntry({ placeholder, accessibilityLabel, onPress }: SbSearchEntryProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <SbPressable accessibilityLabel={accessibilityLabel} accessibilityRole="search" onPress={onPress}>
      <View style={styles.wrap}>
        <View style={styles.entry}>
          <MaterialIcons name="search" size={sbSize.rowIcon} color={colors.tertiaryLabel} />
          <SbText variant="rowTitle" color={colors.tertiaryLabel} numberOfLines={sbMaximumLines.singleLine}>
            {placeholder}
          </SbText>
        </View>
      </View>
    </SbPressable>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    wrap: { paddingHorizontal: sbSpacing.pageHorizontal, paddingBottom: sbSpacing.small },
    entry: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: sbSpacing.small,
      minHeight: sbSize.minimumTouchTarget,
      paddingHorizontal: sbSpacing.compact,
      borderRadius: sbRadius.pill,
      backgroundColor: colors.surfaceRaised,
    },
  });
