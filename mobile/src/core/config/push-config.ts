/**
 * 푸시 알림 설정: Expo 프로젝트 ID 는 mobile/.env 의 EXPO_PUBLIC_EAS_PROJECT_ID (빌드 워크플로는 ENV_FILE Secret 으로 .env 를 만듦)
 */
export const pushConfig = {
  // Expo 푸시 토큰 발급에 필요한 프로젝트 ID (빈 값·미설정 > 없음 > 알림을 쓸 수 없는 상태)
  easProjectId: process.env.EXPO_PUBLIC_EAS_PROJECT_ID || undefined,
  // Android 알림 채널: 서버가 보내는 channelId·app.json 의 defaultChannel 과 같아야 함 (tests/config 가 확인)
  urgentAlertChannelId: 'urgent-alerts',
  urgentAlertChannelName: '긴급 보안 소식',
} as const;
