import { router } from 'expo-router';

import { APP_ROUTES } from './app-routes';

/** 탭 밖 화면의 뒤로 가기: 돌아갈 화면이 있으면 뒤로, 없으면 홈 탭 (주소·알림으로 바로 연 화면) */
export function goBackOrHome(): void {
  if (router.canGoBack()) {
    router.back();
    return;
  }
  router.replace(APP_ROUTES.home);
}

/** 홈 탭으로 이동 (탭 밖 화면에서는 그 화면을 닫고 홈) */
export function goHome(): void {
  router.dismissTo(APP_ROUTES.home);
}
