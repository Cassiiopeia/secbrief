import type { ReactNode } from 'react';
import { StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { useSbStyles } from '../theme/use-sb-theme';
import type { SbColors } from '../tokens';

/** 화면 바탕: 테마 바탕색 + 위쪽 안전 영역 (아래쪽은 탭바가 처리) */
export function SbScreen({ children }: { children: ReactNode }) {
  const styles = useSbStyles(createStyles);
  return (
    <SafeAreaView edges={['top']} style={styles.screen}>
      {children}
    </SafeAreaView>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    screen: { flex: 1, backgroundColor: colors.background },
  });
