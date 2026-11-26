import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbMaximumLines, sbRadius, sbSize, sbSpacing } from '../tokens';
import { SbCoverArt, type SbCoverKind, toSbCoverPalette } from './sb-cover-art';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

export interface SbShortcut {
  /** 고유 값 (누름 때 그대로 돌려줌) */
  key: string;
  label: string;
  coverKind: SbCoverKind;
  accessibilityLabel: string;
}

interface SbShortcutGridProps {
  shortcuts: SbShortcut[];
  onShortcutPress: (key: string) => void;
}

/** 바로가기 격자 (한 줄 4칸): 작은 커버 그림 + 이름. 누르면 그 묶음의 목록으로 */
export function SbShortcutGrid({ shortcuts, onShortcutPress }: SbShortcutGridProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <View style={styles.grid}>
      {shortcuts.map((shortcut) => (
        <View key={shortcut.key} style={styles.cell}>
          <SbPressable accessibilityLabel={shortcut.accessibilityLabel} onPress={() => onShortcutPress(shortcut.key)}>
            <View style={styles.shortcut}>
              <View style={styles.art}>
                <SbCoverArt kind={shortcut.coverKind} palette={toSbCoverPalette(colors, 'neutral')} />
              </View>
              <SbText variant="caption" color={colors.label} numberOfLines={sbMaximumLines.singleLine} isCentered>
                {shortcut.label}
              </SbText>
            </View>
          </SbPressable>
        </View>
      ))}
    </View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    grid: { flexDirection: 'row', flexWrap: 'wrap', paddingHorizontal: sbSpacing.tileHorizontal, rowGap: sbSpacing.compact },
    cell: { width: '25%' },
    shortcut: { alignItems: 'center', gap: sbSpacing.small, paddingHorizontal: sbSpacing.extraSmall },
    art: {
      width: sbSize.shortcutArt,
      height: sbSize.shortcutArt,
      borderRadius: sbRadius.tile,
      overflow: 'hidden',
      backgroundColor: colors.surfaceRaised,
    },
  });
