import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { useState } from 'react';
import { Platform, StyleSheet, TextInput, type TextStyle, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbMaximumFontScale, sbRadius, sbSize, sbSpacing, sbTypography } from '../tokens';
import { SbPressable } from './sb-pressable';

interface SbSearchFieldProps {
  value: string;
  placeholder: string;
  accessibilityLabel: string;
  /** 지우기 버튼 읽기 도우미 문구 */
  clearAccessibilityLabel: string;
  maximumLength: number;
  /** 열리자마자 초점(키보드)을 줄지: 검색 화면만 켬, 고르기 화면 안 검색칸은 누를 때만 (기본 꺼짐) */
  shouldFocusOnMount?: boolean;
  onChangeText: (text: string) => void;
}

const FOCUS_BORDER_WIDTH = 2;
// 웹 브라우저 기본 초점 고리 끄기 (outline-style: auto 는 굵기 0 을 무시함). 웹 전용 CSS 값이라 React Native 타입에 없음
const webFocusRingOffStyle = (Platform.OS === 'web' ? { outlineStyle: 'none' } : {}) as unknown as TextStyle;

/** 검색 입력창: 돋보기 + 입력 + (글이 있으면) 지우기 버튼. 초점은 알약 테두리로 표시 */
export function SbSearchField({
  value,
  placeholder,
  accessibilityLabel,
  clearAccessibilityLabel,
  maximumLength,
  shouldFocusOnMount = false,
  onChangeText,
}: SbSearchFieldProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  const [isFocused, setIsFocused] = useState(false);
  const hasText = value.length > 0;
  return (
    <View style={[styles.field, isFocused && styles.focusedField, !hasText && styles.fieldWithoutClear]}>
      <MaterialIcons name="search" size={sbSize.rowIcon} color={colors.tertiaryLabel} />
      <TextInput
        value={value}
        onChangeText={onChangeText}
        onFocus={() => setIsFocused(true)}
        onBlur={() => setIsFocused(false)}
        placeholder={placeholder}
        placeholderTextColor={colors.tertiaryLabel}
        accessibilityLabel={accessibilityLabel}
        maxLength={maximumLength}
        maxFontSizeMultiplier={sbMaximumFontScale.compact}
        autoFocus={shouldFocusOnMount}
        autoCapitalize="none"
        autoCorrect={false}
        returnKeyType="search"
        selectionColor={colors.feature}
        style={styles.input}
      />
      {hasText && (
        <SbPressable accessibilityLabel={clearAccessibilityLabel} onPress={() => onChangeText('')} feedback="dim">
          <View style={styles.clear}>
            <MaterialIcons name="cancel" size={sbSize.rowIcon} color={colors.tertiaryLabel} />
          </View>
        </SbPressable>
      )}
    </View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    // 테두리는 늘 같은 굵기 (초점이 바뀌어도 크기가 흔들리지 않게 색만 바꿈)
    field: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: sbSpacing.small,
      minHeight: sbSize.minimumTouchTarget,
      paddingLeft: sbSpacing.compact,
      borderRadius: sbRadius.pill,
      borderWidth: FOCUS_BORDER_WIDTH,
      borderColor: colors.surfaceRaised,
      backgroundColor: colors.surfaceRaised,
    },
    focusedField: { borderColor: colors.urgent },
    // 지우기 버튼이 없을 때 글이 오른쪽 끝에 붙지 않게
    fieldWithoutClear: { paddingRight: sbSpacing.compact },
    input: {
      ...sbTypography.rowTitle,
      ...webFocusRingOffStyle,
      flex: 1,
      minWidth: 0,
      paddingVertical: sbSpacing.small,
      paddingHorizontal: sbSpacing.extraSmall,
      color: colors.label,
    },
    clear: { width: sbSize.minimumTouchTarget, height: sbSize.minimumTouchTarget, alignItems: 'center', justifyContent: 'center' },
  });
