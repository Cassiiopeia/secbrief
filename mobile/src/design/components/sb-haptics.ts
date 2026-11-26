import * as Haptics from 'expo-haptics';
import { Platform } from 'react-native';

/** 햅틱 3종: selection 고르기(탭·칩), light 가벼운 확인(스위치, 새로고침 끝), success 저장 완료(알림 막대) */
export type SbHapticKind = 'selection' | 'light' | 'success';

/**
 * 가벼운 햅틱. 웹은 생략, 기기가 막아 둔 경우(저전력 모드 등)의 실패는 조용히 넘김
 */
export function playSbHaptic(kind: SbHapticKind): void {
  if (Platform.OS === 'web') return;
  const hapticRequest = requestSbHaptic(kind);
  hapticRequest.catch(() => undefined);
}

/** 햅틱 종류 > 기기 요청 */
function requestSbHaptic(kind: SbHapticKind): Promise<void> {
  switch (kind) {
    case 'selection':
      return Haptics.selectionAsync();
    case 'light':
      return Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    case 'success':
      return Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
  }
}
