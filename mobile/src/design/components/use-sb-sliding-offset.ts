import { useEffect, useRef } from 'react';
import { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';

import { sbMotion } from '../tokens';

/**
 * 선택 표시가 미끄러지는 위치 스타일 (탭바 막대, 세그먼트 알약 공통)
 * 첫 배치는 바로, 그 뒤 목표가 바뀌면 스프링으로 이동. isReady 가 false(너비 재기 전)면 움직이지 않음
 */
export function useSbSlidingOffsetStyle(targetOffset: number, isReady: boolean) {
  const offset = useSharedValue(0);
  const isPlacedRef = useRef(false);
  useEffect(() => {
    if (!isReady) return;
    offset.set(isPlacedRef.current ? withSpring(targetOffset, sbMotion.tabIndicatorSpring) : targetOffset);
    isPlacedRef.current = true;
  }, [isReady, offset, targetOffset]);
  return useAnimatedStyle(() => ({ transform: [{ translateX: offset.value }] }));
}
