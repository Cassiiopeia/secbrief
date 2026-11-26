import { router } from 'expo-router';

import type { SbMastheadAction } from '@/design';

import { APP_ROUTES } from '../navigation/app-routes';

/** 탭 머리의 검색 버튼 (홈·소식·취약점·더보기 공통): 누르면 검색 화면 */
export const SEARCH_MASTHEAD_ACTION: SbMastheadAction = {
  iconName: 'search',
  accessibilityLabel: '소식 검색',
  onPress: () => router.push(APP_ROUTES.search),
};
