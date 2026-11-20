import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbRadius, sbSpacing } from '../tokens';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

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

/** 하단 탭바: 아이콘 + 작은 글자. 선택된 탭은 본문색·굵게 + 위쪽 민트 막대 */
export function SbBottomTabBar({ items, selectedIndex, onTabSelected }: SbBottomTabBarProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <SafeAreaView edges={['bottom']} style={styles.bar}>
      {items.map((item, tabIndex) => {
        const isSelected = tabIndex === selectedIndex;
        const color = isSelected ? colors.label : colors.tertiaryLabel;
        return (
          <SbPressable key={item.label} accessibilityLabel={item.label} onPress={() => onTabSelected(tabIndex)} isFilling>
            <View style={styles.tab}>
              {/* 선택 표시 막대 (자리 유지: 선택 여부로 높이가 흔들리지 않게) */}
              <View style={[styles.indicator, isSelected && styles.indicatorSelected]} />
              <MaterialIcons name={item.iconName} size={ICON_SIZE} color={color} />
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

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    bar: {
      flexDirection: 'row',
      backgroundColor: colors.background,
      borderTopWidth: StyleSheet.hairlineWidth,
      borderTopColor: colors.separator,
    },
    tab: { alignItems: 'center', gap: LABEL_GAP, paddingBottom: sbSpacing.small },
    indicator: {
      width: INDICATOR_WIDTH,
      height: INDICATOR_HEIGHT,
      borderRadius: sbRadius.pill,
      marginBottom: sbSpacing.small - LABEL_GAP,
    },
    indicatorSelected: { backgroundColor: colors.accent },
  });
