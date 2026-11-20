import { StyleSheet, View } from 'react-native';

import { useSbTheme } from '../theme/use-sb-theme';
import { sbSpacing } from '../tokens';
import { SbCapsLabel } from './sb-caps-label';
import { SbText } from './sb-text';

interface SbMastheadProps {
  /** 예: ['SEC', 'BRIEF'] */
  titleLines: string[];
  /** 예: ['11.20 FRI', '5건 중 2건'] */
  trailingLines?: string[];
}

/** 화면 머리: 왼쪽 큰 제목(여러 줄) + 오른쪽 고정폭 라벨(날짜, 읽은 건수 등) */
export function SbMasthead({ titleLines, trailingLines = [] }: SbMastheadProps) {
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
      <View>
        {trailingLines.map((line) => (
          <SbCapsLabel key={line} text={line} color={colors.label} />
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    paddingHorizontal: sbSpacing.pageHorizontal,
    paddingTop: sbSpacing.large,
    paddingBottom: sbSpacing.compact,
  },
  title: { flex: 1 },
});
