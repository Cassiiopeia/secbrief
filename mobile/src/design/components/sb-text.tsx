import type { ReactNode } from 'react';
import { Platform, StyleSheet, Text, type TextStyle } from 'react-native';

import { type SbTextVariant, sbTypography, toSbMaximumFontScale } from '../tokens';

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
// 줄을 바꿀 수 있는 보이지 않는 자리 (ZERO WIDTH SPACE)
const ZERO_WIDTH_SPACE = '\u200B';
const HANGUL_PATTERN = /[가-힣]/;
// 말줄임 기호(… 또는 점 3개 이상) 바로 뒤에 글자가 붙어 있는 자리 (예: 개최...AI)
const ELLIPSIS_BEFORE_TEXT_PATTERN = /(…|\.{3,})(?=\S)/g;

/** 말줄임 기호 뒤에 줄바꿈 자리 넣기 (낱말 단위 줄바꿈이 '개최...AI' 를 한 낱말로 묶지 않게) */
function toBreakableAfterEllipsis(text: string): string {
  return text.replace(ELLIPSIS_BEFORE_TEXT_PATTERN, `$1${ZERO_WIDTH_SPACE}`);
}

/**
 * 한글 낱말이 줄 끝에서 쪼개지지 않게 하는 글 변환 (Android 전용: 낱말 안 글자 사이에 WORD JOINER)
 * iOS 는 lineBreakStrategyIOS, 웹은 word-break: keep-all 로 처리해 글을 건드리지 않는다
 */
function toUnbrokenKoreanWords(text: string): string {
  if (Platform.OS !== 'android') return text;
  // 낱말 경계 2개: 빈칸, 말줄임 뒤 줄바꿈 자리
  return text
    .split(ZERO_WIDTH_SPACE)
    .map((segment) =>
      segment
        .split(' ')
        .map((word) => (HANGUL_PATTERN.test(word) ? Array.from(word).join(WORD_JOINER) : word))
        .join(' '),
    )
    .join(ZERO_WIDTH_SPACE);
}

// 웹 전용 CSS 값이라 React Native 타입에 없음
const webKeepAllStyle = { wordBreak: 'keep-all' } as unknown as TextStyle;

/**
 * 앱 공통 글자: 글자 토큰 + 색 + 한글 낱말 단위 줄바꿈(말줄임 기호 뒤는 줄바꿈 허용). 화면은 Text 를 직접 쓰지 않고 이것만 쓴다
 */
export function SbText({ variant, color, children, numberOfLines, isCentered = false }: SbTextProps) {
  const content = typeof children === 'string' ? toUnbrokenKoreanWords(toBreakableAfterEllipsis(children)) : children;
  return (
    <Text
      numberOfLines={numberOfLines}
      lineBreakStrategyIOS="hangul-word"
      // 글자 크기 확대는 받되 자리가 깨지지 않는 한도까지
      maxFontSizeMultiplier={toSbMaximumFontScale(variant)}
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
