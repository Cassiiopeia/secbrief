import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { useSbTheme } from '../theme/use-sb-theme';
import { sbMaximumLines, sbSize, sbSpacing } from '../tokens';
import { SbIconButton } from './sb-icon-button';
import { SbText } from './sb-text';

interface SbScreenHeaderProps {
  /** 제목 (children 을 주면 제목 대신 그것을 놓음: 검색 입력창) */
  title?: string;
  backAccessibilityLabel: string;
  onBackPress: () => void;
  children?: ReactNode;
}

/** 탭 밖 화면 머리: 뒤로 버튼 + 제목 (또는 검색 입력창) */
export function SbScreenHeader({ title, backAccessibilityLabel, onBackPress, children }: SbScreenHeaderProps) {
  const { colors } = useSbTheme();
  return (
    <View style={styles.header}>
      <SbIconButton iconName="arrow-back" accessibilityLabel={backAccessibilityLabel} onPress={onBackPress} />
      <View style={styles.center} accessibilityRole={children === undefined ? 'header' : undefined}>
        {children ?? (
          <SbText variant="headline" color={colors.label} numberOfLines={sbMaximumLines.singleLine}>
            {title}
          </SbText>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: sbSpacing.extraSmall,
    minHeight: sbSize.minimumTouchTarget + sbSpacing.small,
    paddingLeft: sbSpacing.extraSmall,
    paddingRight: sbSpacing.pageHorizontal,
  },
  center: { flex: 1 },
});
