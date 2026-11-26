import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { useEffect, useState, useSyncExternalStore } from 'react';
import { AccessibilityInfo, StyleSheet, View } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useIsMotionReduced } from '../theme/use-is-motion-reduced';
import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbMaximumLines, sbMotion, sbRadius, sbSize, sbSpacing } from '../tokens';
import { playSbHaptic } from './sb-haptics';
import { SbText } from './sb-text';
import { type SbToastMessage, dismissSbToast, readCurrentSbToast, subscribeSbToast } from './sb-toast-store';

/**
 * 알림 막대 그리기 (루트 레이아웃에 하나): 화면 위쪽에서 스프링으로 내려와 잠시 뒤 올라가며 사라짐
 * 누름 없음 (뒤 화면 누름을 막지 않음). 띄울 때 성공 햅틱 + 읽기 도우미 안내. 동작 줄이기면 흐려짐만 없이 바로 나타남·사라짐
 */
export function SbToastHost() {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  const insets = useSafeAreaInsets();
  const isMotionReduced = useIsMotionReduced();
  const toastMessage = useSyncExternalStore(subscribeSbToast, readCurrentSbToast, readCurrentSbToast);
  // 사라지는 동안에도 글을 그리기 위해 마지막 문구를 따로 둠
  const [shownToastMessage, setShownToastMessage] = useState<SbToastMessage>();
  // 보임 진행도 (0 숨김 ~ 1 보임)
  const visibility = useSharedValue(0);

  // 그리는 중 상태 맞추기: 새 문구는 바로 잡고, 동작 줄이기면 내릴 때도 바로 비움
  if (toastMessage !== undefined && toastMessage !== shownToastMessage) setShownToastMessage(toastMessage);
  if (toastMessage === undefined && isMotionReduced && shownToastMessage !== undefined) setShownToastMessage(undefined);

  useEffect(() => {
    if (toastMessage === undefined) {
      if (isMotionReduced) {
        visibility.set(0);
        return;
      }
      // 다 올라간 뒤 글을 치움
      const onHidden = () => setShownToastMessage(undefined);
      visibility.set(
        withTiming(0, { duration: sbMotion.toastHideMilliseconds, easing: sbMotion.concealEasing }, (isFinished) => {
          if (isFinished) scheduleOnRN(onHidden);
        }),
      );
      return;
    }
    playSbHaptic('success');
    AccessibilityInfo.announceForAccessibility(toastMessage.message);
    visibility.set(isMotionReduced ? 1 : withSpring(1, sbMotion.toastSpring));
    const hideTimer = setTimeout(() => dismissSbToast(toastMessage.sequence), sbMotion.toastVisibleMilliseconds);
    return () => clearTimeout(hideTimer);
  }, [isMotionReduced, toastMessage, visibility]);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: Math.min(visibility.value, 1),
    transform: [{ translateY: (1 - visibility.value) * -sbMotion.toastOffset }],
  }));
  // 상태 표시줄 바로 아래 (기기마다 다른 안전 영역이라 StyleSheet 에 둘 수 없음)
  const topInsetStyle = { top: insets.top + sbSpacing.small };

  if (shownToastMessage === undefined) return null;
  return (
    <View pointerEvents="none" style={[styles.layer, topInsetStyle]}>
      <Animated.View style={[styles.toast, animatedStyle]}>
        <MaterialIcons name="check-circle" size={sbSize.rowIcon} color={colors.toastIcon} />
        <SbText variant="button" color={colors.background} numberOfLines={sbMaximumLines.singleLine}>
          {shownToastMessage.message}
        </SbText>
      </Animated.View>
    </View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    layer: { position: 'absolute', left: 0, right: 0, alignItems: 'center', paddingHorizontal: sbSpacing.pageHorizontal },
    // 본문색 알약 (밝은 테마 먹색, 어두운 테마 흰색): 어느 화면 위에서도 바탕과 갈림
    toast: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: sbSpacing.small,
      paddingHorizontal: sbSpacing.medium,
      paddingVertical: sbSpacing.compact,
      borderRadius: sbRadius.pill,
      backgroundColor: colors.label,
    },
  });
