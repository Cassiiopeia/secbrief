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

  test('매퍼(data/mappers)끼리 서로 import 하지 않는다 (순환 import 는 개발 빌드 경고, #87)', () => {
    const mapperFolder = 'data/mappers';
    // 매퍼 이름 > 같은 폴더에서 import 하는 매퍼 이름들
    const importedMappersByMapper = new Map(
      listSourceFiles(mapperFolder).map((file) => [
        path.basename(file).replace(/\.tsx?$/, ''),
        [...readSource(file).matchAll(/from '\.\/([a-z-]+)'/g)].map((match) => match[1]),
      ]),
    );
    const cycles: string[] = [];
    // 깊이 우선으로 따라가며 지나온 길에 다시 나오면 순환
    const visit = (mapperName: string, visitingPath: string[]) => {
      if (visitingPath.includes(mapperName)) {
        cycles.push([...visitingPath.slice(visitingPath.indexOf(mapperName)), mapperName].join(' > '));
        return;
      }
      for (const importedMapper of importedMappersByMapper.get(mapperName) ?? []) visit(importedMapper, [...visitingPath, mapperName]);
    };
    for (const mapperName of importedMappersByMapper.keys()) visit(mapperName, []);
    expect(importedMappersByMapper.size).toBeGreaterThan(1);
    expect(cycles).toEqual([]);
  });

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

// 화면 코드의 움직임·그림 직접 쓰기 금지 (디자인 시스템 기본 부품만: SbEntrance, SbListEntrance, SbReveal, SbPressable,
// SbLoadingSkeleton, SbStateIllustration 이 담긴 상태 화면, showSbToast)
const FORBIDDEN_MOTION_AND_ASSET_PATTERNS: { name: string; pattern: RegExp }[] = [
  { name: 'Reanimated 직접 import (움직임은 디자인 시스템 부품으로)', pattern: /from 'react-native-reanimated'/ },
  { name: 'RN Animated·LayoutAnimation 직접 사용', pattern: /import\s*\{[^}]*\b(Animated|LayoutAnimation)\b[^}]*\}\s*from 'react-native'/ },
  { name: '애니메이션 함수 직접 호출', pattern: /\b(withTiming|withSpring|withDelay|withRepeat|withSequence)\(/ },
  { name: '곡선 직접 지정', pattern: /\bEasing\./ },
  { name: '붙고 떨어지는 움직임 직접 지정 (SbReveal 사용)', pattern: /\b(entering|exiting)=\{/ },
  { name: '지연 숫자', pattern: /\bdelay\s*:\s*\d/ },
  { name: '돌아가는 고리 (로딩 뼈대 SbLoadingSkeleton 사용)', pattern: /ActivityIndicator/ },
  { name: '이미지 직접 사용 (그림은 디자인 시스템 부품으로)', pattern: /from 'expo-image'|<Image[\s>]/ },
  // 글꼴 파일은 루트 레이아웃이 불러옴 (아래 예외 패턴으로 허용)
  { name: '자산 경로 직접 require (글꼴 빼고)', pattern: /require\('@\/assets\/(?!fonts\/)/ },
];

describe('화면 코드의 움직임·그림 직접 쓰기 금지', () => {
  const screenFiles = [...listSourceFiles('app'), ...listSourceFiles('features')];
  for (const { name, pattern } of FORBIDDEN_MOTION_AND_ASSET_PATTERNS) {
    test(`${name} 없음`, () => {
      const violations = screenFiles.filter((file) =>
        readSource(file).split('\n').some((line) => !line.trim().startsWith('//') && pattern.test(line)),
      );
      expect(violations).toEqual([]);
    });
  }
});

// 열리자마자 키보드를 올리는 검색칸 (#119 B3): 예외 목록 파일 > 이유
const SEARCH_FIELD_FOCUS_ON_MOUNT_FILES: Record<string, string> = {
  'features/search/search-screen.tsx': '검색만 하는 화면이라 들어오면 바로 입력',
};

describe('검색칸 키보드는 누를 때만 (#119)', () => {
  test('SbSearchField 는 초점을 기본으로 주지 않는다 (autoFocus 고정값 없음, shouldFocusOnMount 기본 false)', () => {
    const searchFieldSource = readSource('design/components/sb-search-field.tsx');
    expect(searchFieldSource).toMatch(/shouldFocusOnMount = false/);
    expect(searchFieldSource).toMatch(/autoFocus=\{shouldFocusOnMount\}/);
    expect(searchFieldSource.split('\n').some((line) => /^\s*autoFocus\s*$/.test(line))).toBe(false);
  });
  test('화면에서 바로 초점을 주는 검색칸은 예외 목록 화면뿐 (관심 기술 화면은 저장하기를 가리지 않게)', () => {
    const screenFiles = [...listSourceFiles('app'), ...listSourceFiles('features')];
    const focusingFiles = screenFiles.filter((file) => /shouldFocusOnMount|autoFocus/.test(readSource(file)));
    expect(focusingFiles.sort()).toEqual(Object.keys(SEARCH_FIELD_FOCUS_ON_MOUNT_FILES).sort());
  });
});

describe('시각 선택기 읽기 도우미 문구 (#119)', () => {
  test('iOS 선택기에 행의 accessibilityLabel 을 붙인다 (시각 글자가 빠지는 행 방지)', () => {
    expect(readSource('design/components/sb-ios-time-picker.ios.tsx')).toMatch(/accessibilityLabelModifier\(accessibilityLabel\)/);
    expect(readSource('design/components/sb-time-picker-row.tsx')).toMatch(/<SbIosTimePicker[\s\S]*accessibilityLabel=\{accessibilityLabel\}/);
  });
  test('방해 금지 시간 시작·끝 행은 같은 문구 함수를 쓴다', () => {
    const sectionSource = readSource('features/notifications/quiet-hours-settings-section.tsx');
    expect(sectionSource).toMatch(/accessibilityLabel=\{sectionView\.startAccessibilityLabel\}/);
    expect(sectionSource).toMatch(/accessibilityLabel=\{sectionView\.endAccessibilityLabel\}/);
  });
});

// 움직임 값(지속시간·지연·곡선) 원본: 디자인 시스템 안에서도 sb-motion.ts 에만 숫자로 둔다
// 예외 목록: 파일 > 이유
const DESIGN_MOTION_LITERAL_EXCEPTIONS: Record<string, string> = {};

describe('움직임 값 단일 원본', () => {
  test('디자인 시스템 부품에 지속시간·지연 숫자, 곡선 직접 정의가 없다 (sbMotion 토큰 사용)', () => {
    const violations = listSourceFiles(path.join('design', 'components'))
      .filter((file) => !(file in DESIGN_MOTION_LITERAL_EXCEPTIONS))
      .filter((file) =>
        readSource(file)
          .split('\n')
          .some((line) => !line.trim().startsWith('//') && /\b(duration|delay)\s*:\s*\d|\.duration\(\d|Easing\.(in|out|inOut|bezier)\(/.test(line)),
      );
    expect(violations).toEqual([]);
  });

  test('붙고 떨어지는 움직임 정의(entering·exiting 값)는 sb-layout-motion.ts 에만 있다', () => {
    const definingFiles = listSourceFiles('').filter((file) => /new Keyframe\(|\b(FadeIn|FadeOut|ZoomIn|ZoomOut|SlideIn\w*|SlideOut\w*)\b/.test(readSource(file)));
    expect(definingFiles).toEqual([path.join('design', 'components', 'sb-layout-motion.ts')]);
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

describe('기기 알림 연동은 한 곳', () => {
  test('expo-notifications 는 data/sources/device-notification-client.ts 에서만 import 한다', () => {
    expect(listSourceFiles('').filter((file) => /from 'expo-notifications'/.test(readSource(file)))).toEqual([
      path.join('data', 'sources', 'device-notification-client.ts'),
    ]);
  });
});

describe('기기 저장소는 한 곳', () => {
  test('AsyncStorage 는 data/sources/device-json-storage.ts 에서만 import 한다 (키 목록 DEVICE_STORAGE_KEYS)', () => {
    expect(listSourceFiles('').filter((file) => /from '@react-native-async-storage\/async-storage'/.test(readSource(file)))).toEqual([
      path.join('data', 'sources', 'device-json-storage.ts'),
    ]);
  });

  test("저장 키 글자('secbrief.…')는 device-json-storage.ts 에만 있다", () => {
    expect(listSourceFiles('').filter((file) => /['"`]secbrief\.[A-Za-z]/.test(readSource(file)))).toEqual([
      path.join('data', 'sources', 'device-json-storage.ts'),
    ]);
  });
});

describe('날짜·시각 표기는 core/format 한 곳', () => {
  test('core/format 밖에서 Date 부분값(getHours·getMonth 등)·padStart 로 표기를 조립하지 않는다', () => {
    const datePartPattern = /\.(getFullYear|getMonth|getDate|getDay|getHours|getMinutes)\(\)|\.padStart\(/;
    const violations = listSourceFiles('')
      .filter((file) => !file.startsWith(path.join('core', 'format') + path.sep))
      .filter((file) => datePartPattern.test(readSource(file)));
    expect(violations).toEqual([]);
  });
});

// 계층별 금지 라이브러리: 서버 상태(react-query)·화면 이동(expo-router)은 features·app 에서만
const LAYER_FORBIDDEN_LIBRARIES: { library: string; forbiddenLayers: string[] }[] = [
  { library: '@tanstack/react-query', forbiddenLayers: ['core', 'data', 'design'] },
  { library: 'expo-router', forbiddenLayers: ['core', 'data', 'design'] },
];

describe('계층별 금지 라이브러리', () => {
  for (const { library, forbiddenLayers } of LAYER_FORBIDDEN_LIBRARIES) {
    test(`${library} 는 ${forbiddenLayers.join(', ')} 에서 import 하지 않는다`, () => {
      const violations = forbiddenLayers
        .flatMap((layer) => listSourceFiles(layer))
        .filter((file) => readSource(file).includes(`from '${library}'`));
      expect(violations).toEqual([]);
    });
  }

  test('fontWeight 로 굵기를 고르지 않는다 (굵기별 글꼴 이름 sbFontFamily 사용)', () => {
    const violations = listSourceFiles('').filter((file) =>
      readSource(file).split('\n').some((line) => !/^\s*(\/\/|\*)/.test(line) && /\bfontWeight\s*:/.test(line)),
    );
    expect(violations).toEqual([]);
  });
});

// 분류 태그 시드 원본: 서버 마이그레이션의 catalog_tag 분야·생태계 행 (#93)
const SERVER_MIGRATION_ROOT = path.join(__dirname, '../../../server/SB-Web/src/main/resources/db/migration');

/** 서버 시드의 분야·생태계 이름·짧은 이름·code (code 는 '-' 가 든 것만: os·network 같은 흔한 낱말은 다른 뜻으로도 씀) */
function readSeededCatalogLiterals(): string[] {
  const literals = new Set<string>();
  for (const fileName of fs.readdirSync(SERVER_MIGRATION_ROOT).filter((name) => name.endsWith('.sql'))) {
    const migrationSource = fs.readFileSync(path.join(SERVER_MIGRATION_ROOT, fileName), 'utf-8');
    for (const match of migrationSource.matchAll(/\('(?:FIELD|ECOSYSTEM)', '([^']+)', '([^']+)', '([^']+)'/g)) {
      if (match[1].includes('-')) literals.add(match[1]);
      literals.add(match[2]);
      literals.add(match[3]);
    }
  }
  return [...literals];
}

// 예외: 파일 > 글자 > 이유 (분류 태그가 아닌 다른 개념이 같은 낱말을 씀)
const SEEDED_CATALOG_LITERAL_EXCEPTIONS: Record<string, string> = {
  'features/articles/article-labels.ts > 데이터베이스': '소식 주제(topic) 이름: 기술 분야와 다른 분류',
};

describe('분류 태그는 서버 카탈로그가 단일 원본 (#93)', () => {
  test('서버 시드에서 분야·생태계를 읽을 수 있다 (규칙이 비어서 통과하지 않게)', () => {
    expect(readSeededCatalogLiterals().length).toBeGreaterThan(10);
  });

  test('앱 코드에 분야·생태계 이름·code 를 글자로 쓰지 않는다 (목록·이름·묶음은 GET /api/v1/catalog/tags 로만)', () => {
    const seededLiterals = readSeededCatalogLiterals();
    const violations = listSourceFiles('').flatMap((file) =>
      readSource(file)
        .split('\n')
        .filter((line) => !/^\s*(\/\/|\*|\/\*\*)/.test(line))
        .flatMap((line) => seededLiterals.filter((literal) => [`'${literal}'`, `"${literal}"`, `\`${literal}\``].some((quoted) => line.includes(quoted))))
        .map((literal) => `${file} > ${literal}`),
    ).filter((violation) => !(violation in SEEDED_CATALOG_LITERAL_EXCEPTIONS));
    expect(violations).toEqual([]);
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

  test('앱 아이콘·시작 화면 바탕·알림 아이콘 색(app.json)이 brand/brand-colors.json 과 같다', () => {
    const brandColors = JSON.parse(fs.readFileSync(BRAND_COLORS_PATH, 'utf-8'));
    const expoConfig = JSON.parse(fs.readFileSync(path.join(__dirname, '../../app.json'), 'utf-8')).expo;
    const splashOptions = expoConfig.plugins.find(
      (plugin: unknown) => Array.isArray(plugin) && plugin[0] === 'expo-splash-screen',
    )[1];
    expect(expoConfig.android.adaptiveIcon.backgroundColor).toBe(brandColors.appIconBackground);
    expect(splashOptions.backgroundColor).toBe(brandColors.splashBackground.light);
    expect(splashOptions.dark.backgroundColor).toBe(brandColors.splashBackground.dark);
    // 알림 아이콘 색: 보라 (밝은 알림창에서도 보이는 쪽)
    const notificationOptions = expoConfig.plugins.find(
      (plugin: unknown) => Array.isArray(plugin) && plugin[0] === 'expo-notifications',
    )[1];
    expect(notificationOptions.color).toBe(brandColors.feature.light);
  });
});
