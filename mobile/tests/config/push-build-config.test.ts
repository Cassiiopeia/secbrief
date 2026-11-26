/**
 * 푸시 빌드 설정: 워크플로 3개의 프로젝트 ID 주입, 채널 ID 일치, Firebase 설정 파일·iOS 푸시 권한은 있을 때만
 */
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';

import { pushConfig } from '@/core/config/push-config';

const MOBILE_ROOT = path.join(__dirname, '../..');
const WORKFLOW_FILE_NAMES = [
  'PROJECT-REACT-NATIVE-EXPO-ANDROID-PLAYSTORE-CICD.yaml',
  'PROJECT-REACT-NATIVE-EXPO-ANDROID-TEST-APK.yaml',
  'PROJECT-REACT-NATIVE-EXPO-IOS-TESTFLIGHT.yaml',
];
const ANDROID_WORKFLOW_FILE_NAMES = WORKFLOW_FILE_NAMES.slice(0, 2);
const NOTIFICATIONS_PLUGIN_NAME = 'expo-notifications';

const readWorkflow = (fileName: string): string =>
  fs.readFileSync(path.join(MOBILE_ROOT, '../.github/workflows', fileName), 'utf-8');
const staticExpoConfig = JSON.parse(fs.readFileSync(path.join(MOBILE_ROOT, 'app.json'), 'utf-8')).expo;

type PluginEntry = string | [string, Record<string, unknown>];
const findNotificationsPluginOptions = (plugins: PluginEntry[]): Record<string, unknown> =>
  (plugins.find((plugin) => Array.isArray(plugin) && plugin[0] === NOTIFICATIONS_PLUGIN_NAME) as [string, Record<string, unknown>])[1];

interface DynamicExpoConfig {
  version: string;
  android: Record<string, unknown>;
  plugins: PluginEntry[];
}
const toDynamicExpoConfig = jest.requireActual<(input: { config: unknown }) => DynamicExpoConfig>('../../app.config.js');

/** 환경 변수를 바꿔 app.config.js 를 적용한 결과 (끝나면 환경 변수 복구) */
function loadDynamicExpoConfig(environment: Record<string, string | undefined>): DynamicExpoConfig {
  const previousEnvironment = { ...process.env };
  for (const [name, value] of Object.entries(environment)) {
    if (value === undefined) delete process.env[name];
    else process.env[name] = value;
  }
  try {
    return toDynamicExpoConfig({ config: structuredClone(staticExpoConfig) });
  } finally {
    process.env = previousEnvironment; // (성공·실패 공통)
  }
}

test.each(WORKFLOW_FILE_NAMES)('%s 는 Expo 프로젝트 ID 를 Variable 에서 넣는다', (fileName) => {
  expect(readWorkflow(fileName)).toContain('EXPO_PUBLIC_EAS_PROJECT_ID: ${{ vars.EXPO_PUBLIC_EAS_PROJECT_ID }}');
});

test.each(ANDROID_WORKFLOW_FILE_NAMES)('%s 는 Secret 이 있을 때만 prebuild 전에 google-services.json 을 만든다', (fileName) => {
  const workflowText = readWorkflow(fileName);
  const writeStepIndex = workflowText.indexOf('if [ -n "$GOOGLE_SERVICES_JSON" ]; then printf \'%s\' "$GOOGLE_SERVICES_JSON" > google-services.json; fi');
  expect(writeStepIndex).toBeGreaterThan(-1);
  expect(writeStepIndex).toBeLessThan(workflowText.indexOf('npx expo prebuild'));
});

test('app.json 의 기본 알림 채널이 앱·서버가 쓰는 채널 ID 와 같다', () => {
  expect(findNotificationsPluginOptions(staticExpoConfig.plugins).defaultChannel).toBe(pushConfig.urgentAlertChannelId);
  expect(pushConfig.urgentAlertChannelId).toBe('urgent-alerts');
});

test('google-services.json 은 커밋하지 않는다', () => {
  expect(fs.readFileSync(path.join(MOBILE_ROOT, '.gitignore'), 'utf-8')).toMatch(/^google-services\.json$/m);
});

test('Firebase 설정 파일이 없으면 googleServicesFile 을 넣지 않는다', () => {
  const missingFilePath = path.join(os.tmpdir(), 'secbrief-missing-google-services.json');
  const dynamicConfig = loadDynamicExpoConfig({ GOOGLE_SERVICES_JSON_PATH: missingFilePath });
  expect(dynamicConfig.android.googleServicesFile).toBeUndefined();
  // app.json 값은 그대로 (버전은 워크플로가 app.json 에서 관리)
  expect(dynamicConfig.version).toBe(staticExpoConfig.version);
  expect(dynamicConfig.android.package).toBe(staticExpoConfig.android.package);
});

test('Firebase 설정 파일이 있으면 그 경로를 googleServicesFile 로 넣는다', () => {
  const temporaryFolder = fs.mkdtempSync(path.join(os.tmpdir(), 'secbrief-push-config-'));
  const presentFilePath = path.join(temporaryFolder, 'google-services.json');
  fs.writeFileSync(presentFilePath, '{}');
  try {
    expect(loadDynamicExpoConfig({ GOOGLE_SERVICES_JSON_PATH: presentFilePath }).android.googleServicesFile).toBe(presentFilePath);
  } finally {
    fs.rmSync(temporaryFolder, { recursive: true }); // 이 테스트가 만든 임시 폴더 (성공·실패 공통)
  }
});

test('iOS 푸시 권한은 IOS_PUSH_APS_ENVIRONMENT 가 있을 때만 넣는다', () => {
  const disabledOptions = findNotificationsPluginOptions(loadDynamicExpoConfig({ IOS_PUSH_APS_ENVIRONMENT: undefined }).plugins);
  expect(disabledOptions.enableRemoteNotifications).toBe(false);

  const enabledOptions = findNotificationsPluginOptions(loadDynamicExpoConfig({ IOS_PUSH_APS_ENVIRONMENT: 'production' }).plugins);
  expect(enabledOptions).toMatchObject({ enableRemoteNotifications: true, mode: 'production' });
});
