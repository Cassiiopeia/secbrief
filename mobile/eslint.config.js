// https://docs.expo.dev/guides/using-eslint/
const { defineConfig } = require('eslint/config');
const expoConfig = require('eslint-config-expo/flat');

// 색 리터럴 금지: #RGB ~ #RRGGBBAA, rgb()/rgba()/hsl() 문자열
const COLOR_LITERAL_SELECTOR = 'Literal[value=/^(#[0-9a-fA-F]{3,8}|(rgb|rgba|hsl|hsla)\\()/]';
// style 에 객체를 직접 쓰기 금지 (배열 안 객체 포함)
const INLINE_STYLE_SELECTOR = 'JSXAttribute[name.name="style"] ObjectExpression';

module.exports = defineConfig([
  expoConfig,
  { ignores: ['dist/*', '.expo/*'] },
  {
    files: ['src/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-syntax': [
        'error',
        { selector: COLOR_LITERAL_SELECTOR, message: '색 값은 src/design/tokens/sb-colors.ts 에만 둬요. SbColors 토큰을 쓰세요.' },
        { selector: INLINE_STYLE_SELECTOR, message: 'style 에 객체를 직접 쓰지 않아요. StyleSheet(또는 useSbStyles)로 이름을 붙이세요.' },
      ],
    },
  },
  {
    // 예외 1: 색 토큰 원본
    files: ['src/design/tokens/sb-colors.ts'],
    rules: { 'no-restricted-syntax': 'off' },
  },
  {
    // 예외 2: 공통 글자 컴포넌트는 토큰 색을 prop 으로 받아 style 에 넣는 유일한 자리
    files: ['src/design/components/sb-text.tsx'],
    rules: { 'no-restricted-syntax': ['error', { selector: COLOR_LITERAL_SELECTOR, message: '색 값은 토큰을 쓰세요.' }] },
  },
]);
