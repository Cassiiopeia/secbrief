import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbRadius, sbSpacing } from '../tokens';
import { SbCapsLabel } from './sb-caps-label';
import { SbArticleImage } from './sb-article-image';
import type { SbCoverKind } from './sb-cover-art';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

interface SbUrgentTileProps {
  /** 예: '긴급 / CVSS 9.8' */
  capsLabel: string;
  title: string;
  /** 예: 'NVD · 2026.11.23 09:12 · 2시간 전' */
  footerLabel: string;
  /** 없으면 커버 그림 없이 글만 */
  coverKind?: SbCoverKind;
  /** 소식 대표 이미지 주소 (있으면 커버 그림 위에 겹쳐 보임) */
  coverImageUrl?: string;
  onPress?: () => void;
}

// 커버 그림 높이 (시안 150), 제목 위·아래 간격, 아래 줄 위 간격
const COVER_HEIGHT = 150;
const TITLE_TOP_GAP = 14;
const TITLE_BOTTOM_GAP = 22;
const FOOTER_TOP_GAP = 10;
// 제목 줄 수 제한 (원문 설명이 통째로 온 긴 제목이 타일을 화면 밖으로 밀지 않게)
const TITLE_MAXIMUM_LINES = 4;

/** 긴급 타일: (선택) 큰 커버 그림 + 민트 바탕 위 라벨·제목·아래 한 줄 */
export function SbUrgentTile({ capsLabel, title, footerLabel, coverKind, coverImageUrl, onPress }: SbUrgentTileProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <SbPressable accessibilityLabel={`${capsLabel}, ${title}, ${footerLabel}`} onPress={onPress}>
      <View style={styles.tile}>
        {coverKind !== undefined && (
          <View style={styles.cover}>
            <SbArticleImage imageUrl={coverImageUrl} coverKind={coverKind} coverTone="feature" isPulsing />
          </View>
        )}
        <View style={styles.body}>
          <SbCapsLabel text={capsLabel} color={colors.onAccent} />
          <View style={styles.title}>
            <SbText variant="tileTitle" color={colors.onAccent} numberOfLines={TITLE_MAXIMUM_LINES}>
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
