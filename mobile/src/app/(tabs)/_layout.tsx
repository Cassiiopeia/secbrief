import { Tabs } from 'expo-router';

import { SbBottomTabBar, type SbBottomTabItem } from '@/design';

// 탭 순서와 표시 내용 (routeName 은 이 폴더의 파일 이름)
const TAB_ITEMS: (SbBottomTabItem & { routeName: string })[] = [
  { routeName: 'index', iconName: 'home', label: '오늘' },
  { routeName: 'news', iconName: 'article', label: '뉴스' },
  { routeName: 'trend', iconName: 'trending-up', label: '동향' },
  { routeName: 'vulnerability', iconName: 'shield', label: '취약점' },
  { routeName: 'settings', iconName: 'tune', label: '설정' },
];

/** 하단 5탭 틀: 탭바는 디자인 시스템의 SbBottomTabBar 로 그림 */
export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{ headerShown: false }}
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
