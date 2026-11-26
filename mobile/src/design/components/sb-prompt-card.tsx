import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbRadius, sbSpacing } from '../tokens';
import { SbFilledButton } from './sb-filled-button';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

interface SbPromptCardProps {
  title: string;
  message: string;
  primaryLabel: string;
  dismissLabel: string;
  onPrimaryPress: () => void;
  onDismissPress: () => void;
}

/** 부드러운 안내 카드 (#93 관심 기술 처음 안내): 제목 > 설명 > 주 버튼 + 닫기 글자 버튼 */
export function SbPromptCard({ title, message, primaryLabel, dismissLabel, onPrimaryPress, onDismissPress }: SbPromptCardProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <View style={styles.outer}>
      <View style={styles.card}>
        <View accessibilityRole="header">
          <SbText variant="sectionTitle" color={colors.label}>
            {title}
          </SbText>
        </View>
        <SbText variant="body" color={colors.secondaryLabel}>
          {message}
        </SbText>
        <View style={styles.actions}>
          <View style={styles.primary}>
            <SbFilledButton label={primaryLabel} onPress={onPrimaryPress} />
          </View>
          <SbPressable accessibilityLabel={dismissLabel} accessibilityRole="button" onPress={onDismissPress} feedback="dim">
            <View style={styles.dismiss}>
              <SbText variant="button" color={colors.tertiaryLabel}>
                {dismissLabel}
              </SbText>
            </View>
          </SbPressable>
        </View>
      </View>
    </View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    outer: { paddingHorizontal: sbSpacing.tileHorizontal, paddingVertical: sbSpacing.small },
    card: { gap: sbSpacing.small, padding: sbSpacing.medium, borderRadius: sbRadius.tile, backgroundColor: colors.surface },
    actions: { flexDirection: 'row', alignItems: 'center', gap: sbSpacing.compact },
    primary: { flex: 1 },
    dismiss: { paddingHorizontal: sbSpacing.small, paddingVertical: sbSpacing.compact },
  });
