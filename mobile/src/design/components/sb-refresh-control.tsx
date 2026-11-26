import { RefreshControl } from 'react-native';

import type { SbColors } from '../tokens';

interface SbRefreshControlOptions {
  colors: SbColors;
  isRefreshing: boolean;
  onRefresh: () => void;
}

/**
 * 당겨서 새로고침 표시 (모든 탭 공통 색). 스크롤 뷰가 이 요소를 직접 복제해 쓰므로 컴포넌트가 아니라 요소를 돌려줌
 */
export function createSbRefreshControl({ colors, isRefreshing, onRefresh }: SbRefreshControlOptions) {
  return (
    <RefreshControl
      refreshing={isRefreshing}
      onRefresh={onRefresh}
      tintColor={colors.label}
      // Android: 돌아가는 고리 색과 그 바탕
      colors={[colors.label]}
      progressBackgroundColor={colors.surface}
    />
  );
}
