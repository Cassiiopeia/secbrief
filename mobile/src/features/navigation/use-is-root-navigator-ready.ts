import { useNavigationContainerRef } from 'expo-router';
import { useCallback, useSyncExternalStore } from 'react';

/**
 * 최상위 화면 스택이 이동을 받을 준비가 됐는지 (꺼진 앱을 알림으로 열 때 첫 화면이 그려지기 전 이동을 막음)
 * 스택 상태가 바뀔 때마다 다시 확인
 */
export function useIsRootNavigatorReady(): boolean {
  const navigationRef = useNavigationContainerRef();
  const subscribe = useCallback((onChange: () => void) => navigationRef.addListener('state', onChange), [navigationRef]);
  return useSyncExternalStore(subscribe, () => navigationRef.isReady(), () => false);
}
