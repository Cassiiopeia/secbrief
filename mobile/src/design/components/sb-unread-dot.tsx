import { StyleSheet, View } from 'react-native';

import { useSbStyles } from '../theme/use-sb-theme';
import { type SbColors, sbRadius, sbSize } from '../tokens';

/** 안 본 글 점 (알림 종, 알림함 칸·줄 공통). 읽기 도우미에는 붙는 쪽이 글로 알림 */
export function SbUnreadDot() {
  const styles = useSbStyles(createStyles);
  return <View style={styles.dot} accessible={false} importantForAccessibility="no" />;
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    // 긴급 표시 색 (밝은 테마 보라, 어두운 테마 민트: 바탕과 대비되는 쪽)
    dot: { width: sbSize.unreadDot, height: sbSize.unreadDot, borderRadius: sbRadius.pill, backgroundColor: colors.urgent },
  });
