import { Tabs } from 'expo-router';

import { SbBottomTabBar, type SbBottomTabItem, sbMotion, useIsMotionReduced } from '@/design';

/**
 * 하단 5탭 정의 (순서 = 표시 순서, routeName 은 이 폴더의 파일 이름)
 * 홈 오늘의 브리핑 / 소식 뉴스·행사 피드 / 취약점 취약점·보안 공지 / 탐색 검색·키워드 순위·동향·행사 / 더보기 알림·출처·문의·약관
 */
const TAB_ITEMS: (SbBottomTabItem & { routeName: string })[] = [
  { routeName: 'index', iconName: 'home', label: '홈' },
  { routeName: 'news', iconName: 'article', label: '소식' },
  { routeName: 'vulnerability', iconName: 'shield', label: '취약점' },
  { routeName: 'explore', iconName: 'explore', label: '탐색' },
  { routeName: 'settings', iconName: 'more-horiz', label: '더보기' },
];

/** 하단 5탭 틀: 탭바는 디자인 시스템의 SbBottomTabBar 로 그림. 탭을 바꾸면 화면이 짧게 교차하며 바뀜 */
export default function TabsLayout() {
  const isMotionReduced = useIsMotionReduced();
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        animation: isMotionReduced ? 'none' : 'fade',
        transitionSpec: {
          animation: 'timing',
          config: { duration: sbMotion.tabSwitchMilliseconds, easing: sbMotion.tabSwitchEasing },
        },
      }}
      tabBar={({ state, navigateToTab }) => (
        <SbBottomTabBar
          items={TAB_ITEMS}
          // 탭바 순서는 TAB_ITEMS 기준 (라우터가 가진 순서와 다를 수 있어 이름으로 맞춤)
          selectedIndex={TAB_ITEMS.findIndex((item) => item.routeName === state.routes[state.index]?.name)}
          onTabSelected={(tabIndex) => {
            const route = state.routes.find((candidate) => candidate.name === TAB_ITEMS[tabIndex]?.routeName);
            if (route !== undefined) navigateToTab(route.key);
          }}
        />
      )}>
      {TAB_ITEMS.map((item) => (
        <Tabs.Screen key={item.routeName} name={item.routeName} />
      ))}
    </Tabs>
  );
}
