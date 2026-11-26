import type { ReactNode } from 'react';
import { ScrollView, StyleSheet } from 'react-native';

import { sbSpacing } from '../tokens';

interface SbHorizontalRailProps {
  children: ReactNode;
  /** 카드 한 장 너비 (주면 카드 단위로 멈춤) */
  snapItemWidth?: number;
}

/** 가로 스크롤 레일: 카드·칩을 옆으로 넘겨 봄 (좌우는 타일 여백, 사이는 타일 간격) */
export function SbHorizontalRail({ children, snapItemWidth }: SbHorizontalRailProps) {
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      decelerationRate="fast"
      snapToInterval={snapItemWidth === undefined ? undefined : snapItemWidth + sbSpacing.tileGap}
      contentContainerStyle={styles.content}>
      {children}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: sbSpacing.tileHorizontal, gap: sbSpacing.tileGap },
});
