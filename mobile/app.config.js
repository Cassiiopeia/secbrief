/* global __dirname */
// app.json 을 감싸는 동적 설정: 빌드 환경에 따라 달라지는 푸시 설정 2개(Firebase 설정 파일, iOS 푸시 권한)만 더함
// 버전·아이콘 등 고정 값은 app.json 이 원본 (version 워크플로가 app.json 을 직접 고침)
const fs = require('fs');
const path = require('path');

// Firebase 설정 파일 기본 위치 (비밀값이라 커밋하지 않음). GOOGLE_SERVICES_JSON_PATH 가 있으면 그 경로
const DEFAULT_GOOGLE_SERVICES_FILE_PATH = './google-services.json';
const NOTIFICATIONS_PLUGIN_NAME = 'expo-notifications';

module.exports = ({ config }) => ({
  ...config,
  android: { ...config.android, ...toGoogleServicesSetting() },
  plugins: config.plugins.map(toPluginWithIosPushSetting),
});

/** Firebase 설정 파일이 있을 때만 googleServicesFile 설정 (없어도 빌드는 됨, 그 빌드는 Android 푸시를 못 받음) */
function toGoogleServicesSetting() {
  const googleServicesFilePath = process.env.GOOGLE_SERVICES_JSON_PATH || DEFAULT_GOOGLE_SERVICES_FILE_PATH;
  const isGoogleServicesFilePresent = fs.existsSync(path.resolve(__dirname, googleServicesFilePath));
  return isGoogleServicesFilePresent ? { googleServicesFile: googleServicesFilePath } : {};
}

/**
 * expo-notifications 플러그인에 iOS 푸시 권한 더하기 (IOS_PUSH_APS_ENVIRONMENT: development | production, 비어 있으면 권한 없음)
 * 수동 서명 프로파일에 Push Notifications 가 없는데 권한을 넣으면 archive 실패
 */
function toPluginWithIosPushSetting(plugin) {
  if (!Array.isArray(plugin) || plugin[0] !== NOTIFICATIONS_PLUGIN_NAME) return plugin;
  const apsEnvironment = process.env.IOS_PUSH_APS_ENVIRONMENT;
  const iosPushSetting = apsEnvironment
    ? { enableRemoteNotifications: true, mode: apsEnvironment }
    : { enableRemoteNotifications: false };
  return [plugin[0], { ...plugin[1], ...iosPushSetting }];
}
