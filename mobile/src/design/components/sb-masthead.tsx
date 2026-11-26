import { StyleSheet, View } from 'react-native';

import { useSbTheme } from '../theme/use-sb-theme';
import { sbSpacing } from '../tokens';
import type { SbIconName } from './sb-bottom-tab-bar';
import { SbCapsLabel } from './sb-caps-label';
import { SbIconButton } from './sb-icon-button';
import { SbText } from './sb-text';

/** 머리 오른쪽 아이콘 버튼 (예: 검색) */
export interface SbMastheadAction {
  iconName: SbIconName;
  accessibilityLabel: string;
  onPress: () => void;
}

interface SbMastheadProps {
  /** 예: ['SEC', 'BRIEF'] */
  titleLines: string[];
  /** 예: ['11.20 FRI', '오늘 12건'] */
  trailingLines?: string[];
  action?: SbMastheadAction;
}

/** 화면 머리: 왼쪽 큰 제목(여러 줄) + 오른쪽 고정폭 라벨(날짜, 건수 등) + (아이콘 버튼) */
export function SbMasthead({ titleLines, trailingLines = [], action }: SbMastheadProps) {
  const { colors } = useSbTheme();
  return (
    <View style={styles.row}>
      <View style={styles.title} accessibilityRole="header">
        {titleLines.map((line) => (
          <SbText key={line} variant="masthead" color={colors.label}>
            {line}
          </SbText>
        ))}
      </View>
      <View style={styles.trailing}>
        {trailingLines.map((line) => (
          <SbCapsLabel key={line} text={line} color={colors.label} />
        ))}
      </View>
      {action !== undefined && (
        <View style={styles.action}>
          <SbIconButton iconName={action.iconName} accessibilityLabel={action.accessibilityLabel} onPress={action.onPress} />
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: sbSpacing.small,
    paddingHorizontal: sbSpacing.pageHorizontal,
    paddingTop: sbSpacing.large,
    paddingBottom: sbSpacing.compact,
  },
  title: { flex: 1 },
  trailing: { alignItems: 'flex-end' },
  // 아이콘 버튼의 터치 여백만큼 오른쪽·아래로 내밀어 아이콘을 글 끝선에 맞춤
  action: { marginRight: -sbSpacing.small, marginBottom: -sbSpacing.small },
});
