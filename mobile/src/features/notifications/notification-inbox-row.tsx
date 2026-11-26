import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { StyleSheet, View } from 'react-native';

import {
  type SbColors,
  SbBadgeRow,
  SbPressable,
  SbText,
  SbUnreadDot,
  sbMaximumLines,
  sbSize,
  sbSpacing,
  toSbArticleTitleColor,
  useSbStyles,
  useSbTheme,
} from '@/design';

import type { NotificationInboxRowView } from './notification-inbox-view';

/**
 * 알림함 한 줄: 새 글 점 | 긴급 알림은 배지 + 시각 > 제목 > 설명 2줄, 공지사항은 제목 > 설명 2줄 > (고정) 날짜 + 아래 구분선
 * 본 글은 제목을 흐리게 (읽은 소식과 같은 색)
 */
export function NotificationInboxRow({ rowView, onPress }: { rowView: NotificationInboxRowView; onPress: () => void }) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  // 공지사항은 배지가 없어 제목을 먼저, 날짜·고정 표시를 아래에
  const isNotice = rowView.kind === 'NOTICE';
  return (
    <SbPressable accessibilityLabel={rowView.accessibilityLabel} onPress={onPress} feedback="dim">
      <View style={styles.row}>
        <View style={styles.dotColumn}>
          <SbUnreadDot isVisible={rowView.isNew} />
        </View>
        <View style={styles.content}>
          {!isNotice && renderMetaLine()}
          <SbText variant="rowTitle" color={toSbArticleTitleColor(colors, !rowView.isNew)} numberOfLines={sbMaximumLines.compactTitle}>
            {rowView.title}
          </SbText>
          {rowView.detail.length > 0 && (
            <SbText variant="caption" color={colors.tertiaryLabel} numberOfLines={sbMaximumLines.compactTitle}>
              {rowView.detail}
            </SbText>
          )}
          {isNotice && renderMetaLine()}
        </View>
      </View>
    </SbPressable>
  );

  function renderMetaLine() {
    return (
      <View style={styles.metaLine}>
        <View style={styles.badges}>
          {rowView.isPinned && (
            <View style={styles.pinned}>
              <MaterialIcons name="push-pin" size={sbSize.rowIcon} color={colors.urgent} />
              <SbText variant="capsLabel" color={colors.urgent}>
                고정
              </SbText>
            </View>
          )}
          <SbBadgeRow badges={rowView.badges} />
          {isNotice && renderTimeLabel()}
        </View>
        {!isNotice && renderTimeLabel()}
      </View>
    );
  }

  function renderTimeLabel() {
    return (
      <SbText variant="timeLabel" color={colors.tertiaryLabel} numberOfLines={sbMaximumLines.singleLine}>
        {rowView.timeLabel}
      </SbText>
    );
  }
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    row: { flexDirection: 'row', paddingRight: sbSpacing.pageHorizontal },
    // 점 자리는 늘 비워 둠 (새 글 여부로 글 시작 위치가 흔들리지 않게)
    dotColumn: { width: sbSpacing.pageHorizontal, alignItems: 'center', paddingTop: sbSpacing.rowVertical + sbSpacing.small },
    content: {
      flex: 1,
      gap: sbSpacing.extraSmall,
      paddingVertical: sbSpacing.rowVertical,
      borderBottomWidth: StyleSheet.hairlineWidth,
      borderBottomColor: colors.separator,
    },
    metaLine: { flexDirection: 'row', alignItems: 'center', gap: sbSpacing.small },
    badges: { flex: 1, flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: sbSpacing.extraSmall },
    pinned: { flexDirection: 'row', alignItems: 'center', gap: sbSpacing.hairline },
  });
