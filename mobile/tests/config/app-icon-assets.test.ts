/**
 * 앱 아이콘 설정: app.json 이 가리키는 아이콘 7개가 브랜드 원본에서 만든 사본인지, 스토어 규격에 맞는지
 * 원본 brand/app-icon-source.png > brand/generate_platform_assets.py > assets/images (사본은 손으로 고치지 않음)
 */
import * as fs from 'fs';
import * as path from 'path';

const MOBILE_ROOT = path.join(__dirname, '../..');
const BRAND_ROOT = path.join(MOBILE_ROOT, '../brand');
const EXPO_DEFAULT_ICON_FOLDER_NAME = 'expo.icon';
// App Store 아이콘 규격 (정사각형 한 변)
const APP_STORE_ICON_SIZE_PIXELS = 1024;
// Android 알림 아이콘: 24dp 의 4배 (xxxhdpi)
const NOTIFICATION_ICON_SIZE_PIXELS = 96;

const staticExpoConfig = JSON.parse(fs.readFileSync(path.join(MOBILE_ROOT, 'app.json'), 'utf-8')).expo;
const generatorSource = fs.readFileSync(path.join(BRAND_ROOT, 'generate_platform_assets.py'), 'utf-8');

type PluginEntry = string | [string, Record<string, string>];
const findPluginOptions = (pluginName: string): Record<string, string> =>
  (staticExpoConfig.plugins.find((plugin: PluginEntry) => Array.isArray(plugin) && plugin[0] === pluginName) as [string, Record<string, string>])[1];

// app.json 이 가리키는 아이콘 경로 (mobile 기준)
const ICON_PATHS = {
  appIcon: staticExpoConfig.icon as string,
  adaptiveForeground: staticExpoConfig.android.adaptiveIcon.foregroundImage as string,
  adaptiveBackground: staticExpoConfig.android.adaptiveIcon.backgroundImage as string,
  adaptiveMonochrome: staticExpoConfig.android.adaptiveIcon.monochromeImage as string,
  splashIcon: findPluginOptions('expo-splash-screen').image,
  notificationIcon: findPluginOptions('expo-notifications').icon,
  favicon: staticExpoConfig.web.favicon as string,
};

interface PngHeader {
  widthPixels: number;
  heightPixels: number;
  hasAlpha: boolean;
}

// PNG 구조: 서명 8바이트 > 조각(길이 4, 이름 4, 내용, 검사값 4) 반복. 첫 조각 IHDR 의 내용 = 너비 4, 높이 4, 비트 깊이 1, 색 유형 1
const PNG_SIGNATURE_BYTES = 8;
const PNG_CHUNK_OVERHEAD_BYTES = 12;
// 색 유형 4(회색+알파), 6(RGB+알파) 만 알파 채널이 있음. 그 외는 tRNS 조각이 있을 때만 투명
const PNG_ALPHA_COLOR_TYPES = [4, 6];

/** PNG 파일 > 크기와 투명 여부 (알파 채널 또는 tRNS 조각) */
function readPngHeader(relativePath: string): PngHeader {
  const fileBytes = fs.readFileSync(path.join(MOBILE_ROOT, relativePath));
  const chunkNames: string[] = [];
  for (let offset = PNG_SIGNATURE_BYTES; offset < fileBytes.length; offset += fileBytes.readUInt32BE(offset) + PNG_CHUNK_OVERHEAD_BYTES) {
    chunkNames.push(fileBytes.toString('latin1', offset + 4, offset + 8));
  }
  const headerOffset = PNG_SIGNATURE_BYTES + 8;
  return {
    widthPixels: fileBytes.readUInt32BE(headerOffset),
    heightPixels: fileBytes.readUInt32BE(headerOffset + 4),
    hasAlpha: PNG_ALPHA_COLOR_TYPES.includes(fileBytes[headerOffset + 9]) || chunkNames.includes('tRNS'),
  };
}

test('app.json 은 Expo 기본 아이콘(expo.icon)을 쓰지 않는다 (iOS 는 맨 위 icon 을 씀)', () => {
  expect(JSON.stringify(staticExpoConfig)).not.toContain(EXPO_DEFAULT_ICON_FOLDER_NAME);
  expect(staticExpoConfig.ios.icon).toBeUndefined();
});

test('앱 아이콘은 1024x1024 이고 알파 채널이 없다 (App Store 는 투명한 아이콘을 거부)', () => {
  expect(readPngHeader(ICON_PATHS.appIcon)).toEqual({
    widthPixels: APP_STORE_ICON_SIZE_PIXELS,
    heightPixels: APP_STORE_ICON_SIZE_PIXELS,
    hasAlpha: false,
  });
});

test.each(Object.entries(ICON_PATHS))('%s 사본이 있고, 생성 스크립트가 만드는 파일이다', (_iconName, iconPath) => {
  expect(fs.existsSync(path.join(MOBILE_ROOT, iconPath))).toBe(true);
  // 스크립트가 모르는 파일을 app.json 이 가리키면 원본을 바꿔도 그 아이콘만 옛것으로 남음
  expect(path.dirname(iconPath)).toBe('./assets/images');
  expect(generatorSource).toContain(`"${path.basename(iconPath)}"`);
});

test('브랜드 아이콘 원본이 있다', () => {
  expect(fs.existsSync(path.join(BRAND_ROOT, 'app-icon-source.png'))).toBe(true);
});

test('Android 적응형 아이콘: 전경·단색은 투명 배경, 세 장의 크기가 같다', () => {
  const foreground = readPngHeader(ICON_PATHS.adaptiveForeground);
  const monochrome = readPngHeader(ICON_PATHS.adaptiveMonochrome);
  const background = readPngHeader(ICON_PATHS.adaptiveBackground);
  expect(foreground.hasAlpha).toBe(true);
  expect(monochrome.hasAlpha).toBe(true);
  expect(foreground.widthPixels).toBe(foreground.heightPixels);
  expect([monochrome.widthPixels, monochrome.heightPixels]).toEqual([foreground.widthPixels, foreground.heightPixels]);
  expect([background.widthPixels, background.heightPixels]).toEqual([foreground.widthPixels, foreground.heightPixels]);
});

test('알림 아이콘은 96x96 투명 배경이다 (상태 표시줄은 알파만 씀)', () => {
  expect(readPngHeader(ICON_PATHS.notificationIcon)).toEqual({
    widthPixels: NOTIFICATION_ICON_SIZE_PIXELS,
    heightPixels: NOTIFICATION_ICON_SIZE_PIXELS,
    hasAlpha: true,
  });
});

test('시작 화면 아이콘은 정사각형 투명 배경이다 (밝은·어두운 바탕 공용 타일)', () => {
  const splashIcon = readPngHeader(ICON_PATHS.splashIcon);
  expect(splashIcon.widthPixels).toBe(splashIcon.heightPixels);
  expect(splashIcon.hasAlpha).toBe(true);
});
