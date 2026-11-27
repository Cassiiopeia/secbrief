import { useWindowDimensions } from 'react-native';

import { type SbTextVariant, toSbEffectiveFontScale } from '../tokens';

/** 글자 토큰이 지금 그려지는 배율 (기기 글자 크기 설정, 바꾸면 다시 그림) */
export function useSbFontScale(variant: SbTextVariant): number {
  const { fontScale } = useWindowDimensions();
  return toSbEffectiveFontScale(fontScale, variant);
}
