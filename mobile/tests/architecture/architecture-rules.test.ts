/**
 * 아키텍처 규칙 테스트: 계층 의존 방향, 화면 코드의 디자인 값 직접 쓰기 금지, 브랜드 색 일치
 * 소스를 글자로 읽어 검사한다 (실행하지 않음)
 */
import * as fs from 'fs';
import * as path from 'path';

import { sbDarkColors, sbLightColors } from '../../src/design/tokens/sb-colors';

const SOURCE_ROOT = path.join(__dirname, '../../src');
const BRAND_COLORS_PATH = path.join(__dirname, '../../../brand/brand-colors.json');

/** 폴더 아래 모든 .ts / .tsx 파일 (src 기준 상대 경로) */
function listSourceFiles(folder: string): string[] {
  const folderPath = path.join(SOURCE_ROOT, folder);
  if (!fs.existsSync(folderPath)) return [];
  return fs.readdirSync(folderPath, { withFileTypes: true, recursive: true })
    .filter((entry) => entry.isFile() && /\.tsx?$/.test(entry.name))
    .map((entry) => path.relative(SOURCE_ROOT, path.join(entry.parentPath, entry.name)));
}

const readSource = (relativePath: string): string => fs.readFileSync(path.join(SOURCE_ROOT, relativePath), 'utf-8');

// 계층별 import 허용 대상 ('@/계층/...' 기준). 의존 방향: core <- data <- features · design <- app
const ALLOWED_LAYER_IMPORTS: Record<string, string[]> = {
  core: ['core'],
  data: ['core', 'data'],
  design: ['core', 'design'],
  features: ['core', 'data', 'design', 'features'],
  // 라우트는 얇게: 데이터 계층을 직접 부르지 않는다
  app: ['core', 'design', 'features', 'assets'],
};

describe('계층 의존 방향', () => {
  for (const [layer, allowedLayers] of Object.entries(ALLOWED_LAYER_IMPORTS)) {
    test(`${layer} 는 ${allowedLayers.join(', ')} 만 import 한다`, () => {
      const violations: string[] = [];
      for (const file of listSourceFiles(layer)) {
        for (const match of readSource(file).matchAll(/from '@\/([a-z]+)\//g)) {
          if (!allowedLayers.includes(match[1])) violations.push(`${file} > @/${match[1]}`);
        }
        // 상대 경로로 계층을 빠져나가는 import 금지 (계층 밖은 '@/...' 로만)
        if (/from '(\.\.\/){3,}/.test(readSource(file))) violations.push(`${file} > 상대 경로로 계층 이탈`);
      }
      expect(violations).toEqual([]);
    });
  }

  test('화면(app, features)은 디자인 시스템을 @/design 진입점으로만 쓴다', () => {
    const violations = [...listSourceFiles('app'), ...listSourceFiles('features')].filter((file) =>
      /from '@\/design\/.+'/.test(readSource(file)),
    );
    expect(violations).toEqual([]);
  });
});

// 화면 코드에서 금지하는 디자인 값 직접 쓰기 (토큰·컴포넌트만 허용)
const FORBIDDEN_PATTERNS: { name: string; pattern: RegExp }[] = [
  { name: '색 리터럴', pattern: /['"]#[0-9a-fA-F]{3,8}['"]|rgba?\(/ },
  { name: '글꼴 직접 지정', pattern: /fontFamily\s*:/ },
  { name: '글자 크기 숫자', pattern: /fontSize\s*:\s*\d/ },
  { name: '간격 숫자', pattern: /\b(padding|margin|gap)[A-Za-z]*\s*:\s*-?\d/ },
  { name: '모서리 숫자', pattern: /borderRadius\s*:\s*\d/ },
  { name: '지속시간 숫자', pattern: /duration\s*:\s*\d/ },
  { name: 'Text 직접 사용 (SbText 사용)', pattern: /<Text[\s>]/ },
  { name: '다크 판정 직접 (useSbTheme 사용)', pattern: /useColorScheme/ },
];

// 예외 목록: 파일 > 이유. 비어 있는 것이 정상이며 추가할 때 이유를 적는다
const FORBIDDEN_PATTERN_EXCEPTIONS: Record<string, string> = {};

describe('화면 코드의 디자인 값 직접 쓰기 금지', () => {
  const screenFiles = [...listSourceFiles('app'), ...listSourceFiles('features')].filter(
    (file) => !(file in FORBIDDEN_PATTERN_EXCEPTIONS),
  );
  for (const { name, pattern } of FORBIDDEN_PATTERNS) {
    test(`${name} 없음`, () => {
      const violations = screenFiles.filter((file) =>
        readSource(file).split('\n').some((line) => !line.trim().startsWith('//') && pattern.test(line)),
      );
      expect(violations).toEqual([]);
    });
  }

  test('다크 판정(useColorScheme)은 테마 훅 한 곳에만 있다', () => {
    const usingFiles = listSourceFiles('').filter((file) => /useColorScheme\(/.test(readSource(file)));
    expect(usingFiles).toEqual([path.join('design', 'theme', 'use-sb-theme.ts')]);
  });
});

describe('운영 빌드에 샘플 데이터 금지', () => {
  test('src 아래에 sample 파일이 없다', () => {
    expect(listSourceFiles('').filter((file) => /(^|\/)sample-/.test(file))).toEqual([]);
  });
  test('src 어디에서도 sample 모듈을 import 하지 않는다', () => {
    expect(listSourceFiles('').filter((file) => /from '[^']*sample-[^']*'/.test(readSource(file)))).toEqual([]);
  });
});

describe('외부 통신은 API 클라이언트 한 곳', () => {
  test('fetch( 는 data/sources/secbrief-api-client.ts 에만 있다', () => {
    expect(listSourceFiles('').filter((file) => /\bfetch\(/.test(readSource(file)))).toEqual([
      path.join('data', 'sources', 'secbrief-api-client.ts'),
    ]);
  });
});

describe('브랜드 색 단일 원본', () => {
  test('색 토큰이 brand/brand-colors.json 과 같다', () => {
    const brandColors: Record<string, { light: string; dark: string } | string> = JSON.parse(
      fs.readFileSync(BRAND_COLORS_PATH, 'utf-8'),
    );
    const tokenKeys = ['accent', 'onAccent', 'feature', 'onFeature', 'background', 'surface', 'surfaceRaised', 'label'] as const;
    for (const key of tokenKeys) {
      expect({ key, light: sbLightColors[key], dark: sbDarkColors[key] }).toEqual({ key, ...(brandColors[key] as object) });
    }
  });

  test('앱 아이콘·시작 화면 바탕(app.json)이 brand/brand-colors.json 과 같다', () => {
    const brandColors = JSON.parse(fs.readFileSync(BRAND_COLORS_PATH, 'utf-8'));
    const expoConfig = JSON.parse(fs.readFileSync(path.join(__dirname, '../../app.json'), 'utf-8')).expo;
    const splashOptions = expoConfig.plugins.find(
      (plugin: unknown) => Array.isArray(plugin) && plugin[0] === 'expo-splash-screen',
    )[1];
    expect(expoConfig.android.adaptiveIcon.backgroundColor).toBe(brandColors.appIconBackground);
    expect(splashOptions.backgroundColor).toBe(brandColors.splashBackground.light);
    expect(splashOptions.dark.backgroundColor).toBe(brandColors.splashBackground.dark);
  });
});
