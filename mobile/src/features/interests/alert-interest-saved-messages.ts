import type { AlertDigestSettings } from '@/data/models/alert-interest';

/** 저장한 설정 종류 3개: 관심 기술(관심 기술 화면), 아침 요약 켬·끔, 아침 요약 받는 시각 (더보기) */
export type AlertInterestSettingChange = 'interests' | 'digestDeliveryMode' | 'digestTime';

// 저장 확인 문구 (알림 막대) 단일 원본
const ALERT_INTEREST_SAVED_MESSAGES = {
  interests: '관심 기술을 저장했어요',
  // 알림을 못 쓰는 기기(웹)·받을 것이 없는 기기: 서버에 보내지 않고 기기에만
  interestsOnDeviceOnly: '관심 기술을 이 기기에 저장했어요. 알림은 앱에서 받을 수 있어요',
  digestTurnedOn: '아침 요약을 켰어요',
  digestTurnedOff: '아침 요약을 껐어요',
  digestTimeChanged: '아침 요약 받는 시각을 바꿨어요',
} as const;

/** 저장한 설정 종류 + 저장된 받는 방식 + 서버 전송 여부 > 알림 막대 문구 */
export function toAlertInterestSavedMessage(change: AlertInterestSettingChange, savedDigest: AlertDigestSettings, isSentToServer: boolean): string {
  if (change === 'digestDeliveryMode') {
    return savedDigest.deliveryMode === 'DIGEST' ? ALERT_INTEREST_SAVED_MESSAGES.digestTurnedOn : ALERT_INTEREST_SAVED_MESSAGES.digestTurnedOff;
  }
  if (change === 'digestTime') return ALERT_INTEREST_SAVED_MESSAGES.digestTimeChanged;
  return isSentToServer ? ALERT_INTEREST_SAVED_MESSAGES.interests : ALERT_INTEREST_SAVED_MESSAGES.interestsOnDeviceOnly;
}
