import Constants from 'expo-constants';

/** 앱 정보: 버전은 app.json 의 expo.version (못 읽으면 알 수 없음) */
export const appInfo = {
  version: Constants.expoConfig?.version ?? '알 수 없음',
} as const;
