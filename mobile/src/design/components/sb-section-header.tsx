import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { StyleSheet, View } from 'react-native';

import { useSbTheme } from '../theme/use-sb-theme';
import { sbSize, sbSpacing } from '../tokens';
import type { SbIconName } from './sb-bottom-tab-bar';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

/** 구역 제목 오른쪽 이동 버튼 (예: 전체 보기) */
export interface SbSectionAction {
  label: string;
  accessibilityLabel: string;
  onPress: () => void;
}

interface SbSectionHeaderProps {
  title: string;
  /** 제목 앞 작은 아이콘 (예: AI 가 고른 구역의 auto-awesome) */
  leadingIconName?: SbIconName;
  /** 제목 아래 한 줄 설명 */
  caption?: string;
  action?: SbSectionAction;
}

const LEADING_ICON_SIZE = 16;
const ACTION_ICON_SIZE = 18;

/** 구역 제목: 모든 탭에서 같은 크기·여백 ((아이콘) 제목 + (설명) + 오른쪽 이동 버튼) */
export function SbSectionHeader({ title, leadingIconName, caption, action }: SbSectionHeaderProps) {
  const { colors } = useSbTheme();
  return (
    <View style={styles.header}>
      <View style={styles.text}>
        <View style={styles.title} accessibilityRole="header">
          {leadingIconName !== undefined && <MaterialIcons name={leadingIconName} size={LEADING_ICON_SIZE} color={colors.urgent} />}
          <SbText variant="sectionTitle" color={colors.label}>
            {title}
          </SbText>
        </View>
        {caption !== undefined && (
          <SbText variant="caption" color={colors.tertiaryLabel}>
            {caption}
          </SbText>
        )}
      </View>
      {action !== undefined && (
        <SbPressable accessibilityLabel={action.accessibilityLabel} onPress={action.onPress} feedback="dim">
          <View style={styles.action}>
            <SbText variant="button" color={colors.secondaryLabel}>
              {action.label}
            </SbText>
            <MaterialIcons name="chevron-right" size={ACTION_ICON_SIZE} color={colors.secondaryLabel} />
          </View>
        </SbPressable>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: sbSpacing.small,
    paddingHorizontal: sbSpacing.pageHorizontal,
    paddingTop: sbSpacing.large,
    paddingBottom: sbSpacing.small,
  },
  text: { flex: 1, gap: sbSpacing.hairline },
  title: { flexDirection: 'row', alignItems: 'center', gap: sbSpacing.extraSmall },
  // 글 줄보다 터치 영역을 키우되 아래 여백이 늘지 않게 음수 여백으로 상쇄
  action: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: sbSize.minimumTouchTarget,
    marginVertical: -sbSpacing.compact,
    marginRight: -sbSpacing.extraSmall,
  },
});
