import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';

import { sbFontFamily } from '@/design';
import { NotificationLaunchEffects } from '@/features/notifications/notification-launch-effects';

// 글꼴을 다 불러올 때까지 시작 화면 유지
SplashScreen.preventAutoHideAsync();

// 서버 상태 캐시 (앱 전체에서 하나)
const queryClient = new QueryClient();

/** 앱 최상위: 글꼴 불러오기 > 서버 상태 캐시 > 화면 스택 > 알림 시작 처리 */
export default function RootLayout() {
  const [areFontsLoaded, fontError] = useFonts({
    [sbFontFamily.sansRegular]: require('@/assets/fonts/Pretendard-Regular.ttf'),
    [sbFontFamily.sansMedium]: require('@/assets/fonts/Pretendard-Medium.ttf'),
    [sbFontFamily.sansSemiBold]: require('@/assets/fonts/Pretendard-SemiBold.ttf'),
    [sbFontFamily.sansBold]: require('@/assets/fonts/Pretendard-Bold.ttf'),
    [sbFontFamily.sansExtraBold]: require('@/assets/fonts/Pretendard-ExtraBold.otf'),
    [sbFontFamily.monoRegular]: require('@/assets/fonts/D2Coding-Regular.ttf'),
    [sbFontFamily.monoBold]: require('@/assets/fonts/D2Coding-Bold.ttf'),
  });
  // 글꼴 불러오기에 실패해도 기본 글꼴로 화면은 연다
  const isReady = areFontsLoaded || fontError !== null;

  useEffect(() => {
    if (isReady) SplashScreen.hideAsync();
  }, [isReady]);

  if (!isReady) return null;

  return (
    <QueryClientProvider client={queryClient}>
      <StatusBar style="auto" />
      <Stack screenOptions={{ headerShown: false }} />
      <NotificationLaunchEffects />
    </QueryClientProvider>
  );
}
