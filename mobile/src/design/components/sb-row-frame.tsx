import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbSize, sbSpacing } from '../tokens';
import { SbText } from './sb-text';

interface SbRowFrameProps {
  title: string;
  detail?: string;
  /** 바꿀 수 없는 행의 제목을 흐리게 */
  isTitleDimmed?: boolean;
  /** 오른쪽에 놓이는 것 (아이콘, 스위치) */
  trailing?: ReactNode;
  /** 누를 수 없는 정보 행을 한 덩어리로 읽어 줄 때의 문구 (누르는 행은 바깥 래퍼가 읽음) */
  accessibilityLabel?: string;
}

/** 설정·목록 행 틀: 제목 + (설명) + (오른쪽 요소) + 글 시작 위치에 맞춘 아래 구분선. SbListRow 와 SbSwitchRow 가 함께 씀 */
export function SbRowFrame({ title, detail, isTitleDimmed = false, trailing, accessibilityLabel }: SbRowFrameProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <View style={styles.row} accessible={accessibilityLabel !== undefined} accessibilityLabel={accessibilityLabel}>
      <View style={styles.content}>
        <View style={styles.text}>
          <SbText variant="rowTitle" color={isTitleDimmed ? colors.tertiaryLabel : colors.label}>
            {title}
          </SbText>
          {detail !== undefined && (
            <SbText variant="caption" color={colors.tertiaryLabel}>
              {detail}
            </SbText>
          )}
        </View>
        {trailing}
      </View>
    </View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    row: { paddingHorizontal: sbSpacing.pageHorizontal },
    content: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: sbSpacing.compact,
      minHeight: sbSize.minimumTouchTarget + sbSpacing.small,
      paddingVertical: sbSpacing.compact,
      borderBottomWidth: StyleSheet.hairlineWidth,
      borderBottomColor: colors.separator,
    },
    text: { flex: 1, gap: sbSpacing.hairline },
  });
