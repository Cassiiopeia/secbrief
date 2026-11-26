import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbMotion, sbRadius, sbSize, sbSpacing } from '../tokens';
import { playSbHaptic } from './sb-haptics';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';
import { useSbSlidingOffsetStyle } from './use-sb-sliding-offset';

export type SbIconName = keyof typeof MaterialIcons.glyphMap;

/** 하단 탭 한 칸의 내용 */
export interface SbBottomTabItem {
  iconName: SbIconName;
  label: string;
}

interface SbBottomTabBarProps {
  items: SbBottomTabItem[];
  selectedIndex: number;
  onTabSelected: (tabIndex: number) => void;
}

const ICON_SIZE = 22;
const INDICATOR_WIDTH = 20;
const INDICATOR_HEIGHT = 3;
const LABEL_GAP = 3;

/** 하단 탭바: 아이콘 + 작은 글자. 선택된 탭은 본문색·굵게, 위쪽 민트 막대가 선택된 칸으로 미끄러져 옴 */
export function SbBottomTabBar({ items, selectedIndex, onTabSelected }: SbBottomTabBarProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  const [barWidth, setBarWidth] = useState(0);
  // 선택된 칸 가운데에 막대를 맞춤
  const tabWidth = barWidth / items.length;
  const indicatorStyle = useSbSlidingOffsetStyle(selectedIndex * tabWidth + (tabWidth - INDICATOR_WIDTH) / 2, barWidth > 0);

  const onBarLayout = (event: LayoutChangeEvent) => setBarWidth(event.nativeEvent.layout.width);
  const onTabPressed = (tabIndex: number) => {
    if (tabIndex !== selectedIndex) playSbHaptic('selection');
    onTabSelected(tabIndex);
  };

  return (
    <SafeAreaView edges={['bottom']} style={styles.bar} onLayout={onBarLayout} accessibilityRole="tablist">
      {barWidth > 0 && <Animated.View style={[styles.indicator, indicatorStyle]} />}
      {items.map((item, tabIndex) => {
        const isSelected = tabIndex === selectedIndex;
        const color = isSelected ? colors.label : colors.tertiaryLabel;
        return (
          <SbPressable
            key={item.label}
            accessibilityLabel={item.label}
            accessibilityRole="tab"
            accessibilityState={{ selected: isSelected }}
            onPress={() => onTabPressed(tabIndex)}
            feedback="dim"
            isFilling>
            <View style={styles.tab}>
              <SbTabIcon iconName={item.iconName} color={color} isSelected={isSelected} />
              <SbText variant={isSelected ? 'tabLabelSelected' : 'tabLabel'} color={color}>
                {item.label}
              </SbText>
            </View>
          </SbPressable>
        );
      })}
    </SafeAreaView>
  );
}

interface SbTabIconProps {
  iconName: SbIconName;
  color: string;
  isSelected: boolean;
}

/** 탭 아이콘: 선택되면 살짝 커짐 */
function SbTabIcon({ iconName, color, isSelected }: SbTabIconProps) {
  const scale = useSharedValue(isSelected ? sbMotion.tabIconSelectedScale : 1);
  useEffect(() => {
    scale.set(withSpring(isSelected ? sbMotion.tabIconSelectedScale : 1, sbMotion.tabIndicatorSpring));
  }, [isSelected, scale]);
  const animatedStyle = useAnimatedStyle(() => ({ transform: [{ scale: scale.value }] }));
  return (
    <Animated.View style={animatedStyle}>
      <MaterialIcons name={iconName} size={ICON_SIZE} color={color} />
    </Animated.View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    bar: {
      flexDirection: 'row',
      backgroundColor: colors.background,
      borderTopWidth: StyleSheet.hairlineWidth,
      borderTopColor: colors.separator,
    },
    tab: {
      alignItems: 'center',
      justifyContent: 'center',
      gap: LABEL_GAP,
      minHeight: sbSize.minimumTouchTarget,
      paddingTop: INDICATOR_HEIGHT + sbSpacing.small,
      paddingBottom: sbSpacing.small,
    },
    // 탭바 맨 위 왼쪽 기준에서 선택된 칸 가운데로 옮겨짐
    indicator: {
      position: 'absolute',
      top: 0,
      left: 0,
      width: INDICATOR_WIDTH,
      height: INDICATOR_HEIGHT,
      borderBottomLeftRadius: sbRadius.pill,
      borderBottomRightRadius: sbRadius.pill,
      backgroundColor: colors.accent,
    },
  });
