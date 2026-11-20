import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbRadius, sbSpacing } from '../tokens';
import { SbCapsLabel } from './sb-caps-label';
import { SbCoverArt, type SbCoverKind, toSbCoverPalette } from './sb-cover-art';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

interface SbUrgentTileProps {
  /** 예: '긴급 / CVSS 9.8' */
  capsLabel: string;
  title: string;
  /** 예: 'Tomcat · 내 스택 해당' */
  footerLabel: string;
  /** 없으면 커버 그림 없이 글만 */
  coverKind?: SbCoverKind;
  onPress?: () => void;
}

// 커버 그림 높이 (시안 150), 제목 위·아래 간격, 아래 줄 위 간격
const COVER_HEIGHT = 150;
const TITLE_TOP_GAP = 14;
const TITLE_BOTTOM_GAP = 22;
const FOOTER_TOP_GAP = 10;

/** 긴급 타일: (선택) 큰 커버 그림 + 민트 바탕 위 라벨·제목·아래 한 줄 */
export function SbUrgentTile({ capsLabel, title, footerLabel, coverKind, onPress }: SbUrgentTileProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <SbPressable accessibilityLabel={title} onPress={onPress}>
      <View style={styles.tile}>
        {coverKind !== undefined && (
          <View style={styles.cover}>
            <SbCoverArt kind={coverKind} palette={toSbCoverPalette(colors, 'feature')} isPulsing />
          </View>
        )}
        <View style={styles.body}>
          <SbCapsLabel text={capsLabel} color={colors.onAccent} />
          <View style={styles.title}>
            <SbText variant="tileTitle" color={colors.onAccent}>
              {title}
            </SbText>
          </View>
          <View style={styles.footer}>
            <View style={styles.footerLabel}>
              <SbCapsLabel text={footerLabel} color={colors.onAccent} />
            </View>
            <SbCapsLabel text="→" color={colors.onAccent} />
          </View>
        </View>
      </View>
    </SbPressable>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    tile: { backgroundColor: colors.accent, borderRadius: sbRadius.tile, overflow: 'hidden' },
    cover: { height: COVER_HEIGHT },
    body: { padding: sbSpacing.tilePadding },
    title: { marginTop: TITLE_TOP_GAP, marginBottom: TITLE_BOTTOM_GAP },
    footer: {
      flexDirection: 'row',
      paddingTop: FOOTER_TOP_GAP,
      borderTopWidth: StyleSheet.hairlineWidth * 2,
      borderTopColor: colors.onAccent,
    },
    footerLabel: { flex: 1 },
  });
