import { useMemo } from 'react';
import { useColorScheme } from 'react-native';

import { type SbColors, sbDarkColors, sbLightColors } from '../tokens';

export interface SbTheme {
  colors: SbColors;
  isDark: boolean;
}

/**
 * 현재 테마 (기기의 라이트·다크 설정을 따름). 다크 판정은 이 훅 한 곳에서만 한다
 */
export function useSbTheme(): SbTheme {
  const isDark = useColorScheme() === 'dark';
  return useMemo(() => ({ colors: isDark ? sbDarkColors : sbLightColors, isDark }), [isDark]);
}

/**
 * 테마 색을 입힌 스타일: createStyles(colors) 결과를 테마가 바뀔 때만 다시 만든다
 * 컴포넌트는 style 에 객체를 직접 쓰지 않고 이 훅이 돌려준 이름으로만 고른다
 */
export function useSbStyles<Styles>(createStyles: (colors: SbColors) => Styles): Styles {
  const { colors } = useSbTheme();
  return useMemo(() => createStyles(colors), [createStyles, colors]);
}
