import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { useSbStyles } from '../theme/use-sb-theme';
import { type SbColors, sbSpacing } from '../tokens';

interface SbBottomActionBarProps {
  /** 주 버튼 (남은 너비를 채움) */
  primaryAction: ReactNode;
  /** 그 옆 작은 버튼 (예: 공유 아이콘) */
  secondaryAction?: ReactNode;
}

/** 화면 아래 고정 버튼 줄 (스크롤해도 항상 보임, 아래쪽 안전 영역 포함) */
export function SbBottomActionBar({ primaryAction, secondaryAction }: SbBottomActionBarProps) {
  const styles = useSbStyles(createStyles);
  return (
    <SafeAreaView edges={['bottom']} style={styles.bar}>
      <View style={styles.actions}>
        <View style={styles.primary}>{primaryAction}</View>
        {secondaryAction}
      </View>
    </SafeAreaView>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    bar: { backgroundColor: colors.background, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.separator },
    actions: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: sbSpacing.small,
      paddingHorizontal: sbSpacing.pageHorizontal,
      paddingVertical: sbSpacing.small,
    },
    primary: { flex: 1 },
  });
