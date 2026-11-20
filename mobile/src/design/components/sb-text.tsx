import type { ReactNode } from 'react';
import { Platform, StyleSheet, Text, type TextStyle } from 'react-native';

import { type SbTextVariant, sbTypography } from '../tokens';

interface SbTextProps {
  variant: SbTextVariant;
  /** SbColors 의 값 */
  color: string;
  children: ReactNode;
  numberOfLines?: number;
  isCentered?: boolean;
}

// 글자 사이에 넣으면 그 자리에서 줄이 끊기지 않는 보이지 않는 문자 (WORD JOINER)
const WORD_JOINER = '⁠';
const HANGUL_PATTERN = /[가-힣]/;

/**
 * 한글 낱말이 줄 끝에서 쪼개지지 않게 하는 글 변환 (Android 전용: 낱말 안 글자 사이에 WORD JOINER)
 * iOS 는 lineBreakStrategyIOS, 웹은 word-break: keep-all 로 처리해 글을 건드리지 않는다
 */
function toUnbrokenKoreanWords(text: string): string {
  if (Platform.OS !== 'android') return text;
  return text
    .split(' ')
    .map((word) => (HANGUL_PATTERN.test(word) ? Array.from(word).join(WORD_JOINER) : word))
    .join(' ');
}

// 웹 전용 CSS 값이라 React Native 타입에 없음
const webKeepAllStyle = { wordBreak: 'keep-all' } as unknown as TextStyle;

/**
 * 앱 공통 글자: 글자 토큰 + 색 + 한글 낱말 단위 줄바꿈. 화면은 Text 를 직접 쓰지 않고 이것만 쓴다
 */
export function SbText({ variant, color, children, numberOfLines, isCentered = false }: SbTextProps) {
  const content = typeof children === 'string' ? toUnbrokenKoreanWords(children) : children;
  return (
    <Text
      numberOfLines={numberOfLines}
      lineBreakStrategyIOS="hangul-word"
      // 색은 토큰 값을 그대로 받는 유일한 자리 (규칙 예외: eslint.config.js)
      style={[sbTypography[variant], styles.base, isCentered && styles.centered, { color }]}>
      {content}
    </Text>
  );
}

const styles = StyleSheet.create({
  base: Platform.OS === 'web' ? webKeepAllStyle : {},
  centered: { textAlign: 'center' },
});
